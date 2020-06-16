package uk.gov.defra.ffc

class Helm implements Serializable {
  static def writeUrlIfIngress(ctx, deploymentName) {
    ctx.sh("kubectl get ingress -n $deploymentName -o json --ignore-not-found | jq '.items[0].spec.rules[0].host // empty' | xargs --no-run-if-empty printf 'Build available for review at https://%s\n'")
  }

  static def addHelmRepo(ctx, repoName, url) {
    ctx.sh("helm repo add $repoName $url")
    ctx.sh('helm repo update')
  }

  static def getExtraCommands(tag) {
    return "--set labels.version=$tag --install --atomic --version=$tag"
  }

  static def getPrCommands(registry, chartName, tag, buildNumber) {
    def flags = [
      /image=$registry\/$chartName:$tag/,
      /namespace=$chartName-$tag/,
      /pr=$tag/,
      /deployment.redeployOnChange=$tag-$buildNumber/
      ].join(',')
    return "--set $flags"
  }

  static def getValuesFromAppConfig(ctx, configKeys, prefix, label='\\\\0', failIfNotFound=true, delimiter='/') {
    def configItems = [:]
    def suppressConsoleOutput = '#!/bin/bash +x\n'

    ctx.echo("Running with $label, $failIfNotFound, $delimiter")

    for ( int i = 0 ; i < configKeys.size() ; i++ ) {
      def key = configKeys[i]
      def appConfigResults = ctx.sh(returnStdout: true, script:"$suppressConsoleOutput az appconfig kv list --subscription \$APP_CONFIG_SUBSCRIPTION --name \$APP_CONFIG_NAME --key $prefix$delimiter$key --label $label --resolve-keyvault").trim()

      // Check value. It should alway be only one string
      def numResults = ctx.sh(returnStdout: true, script:"$suppressConsoleOutput jq -n '$appConfigResults | length'").trim()

      if (numResults == '1') {
          def value = ctx.sh(returnStdout: true, script:"$suppressConsoleOutput jq -n '$appConfigResults | .[0] | .value'").trim()
          configItems[key] = value
      }
      else if (numResults == '0' && !failIfNotFound) { }
      else {
          throw new Exception("Unexpected number of results from App Configuration when retrieving $key: $numResults")
      }
    }

    return configItems
  }

  static def configItemsToSetString(configItems) {
    return configItems.size() > 0 ? '--set ' + configItems.collect { "$it.key=$it.value" }.join(',') : ''
  }

  static def deployChart(ctx, environment, registry, chartName, tag) {
    ctx.withKubeConfig([credentialsId: "kubeconfig-$environment"]) {
      ctx.withCredentials([
        ctx.file(credentialsId: "$chartName-$environment-values", variable: 'envValues'),
        ctx.file(credentialsId: "$chartName-pr-values", variable: 'prValues'),
      ]) {
        def deploymentName = "$chartName-$tag"
        def extraCommands = Helm.getExtraCommands(tag)
        def prCommands = Helm.getPrCommands(registry, chartName, tag, ctx.BUILD_NUMBER)

        def keysFileContent = ctx.readFile("helm/$chartName/deployment-keys.txt")
        def configKeys = keysFileContent.tokenize('\n')
        def defaultConfigValues = configItemsToSetString(getValuesFromAppConfig(ctx, configKeys, environment))
        def prConfigValues = configItemsToSetString(getValuesFromAppConfig(ctx, configKeys, environment, 'pr', false))
        def suppressConsoleOutput = '#!/bin/bash +x\n'

        echo "$defaultConfigValues"
        echo "$prConfigValues"

        ctx.sh("kubectl get namespaces $deploymentName || kubectl create namespace $deploymentName")
        ctx.sh("$suppressConsoleOutput helm upgrade $deploymentName --namespace=$deploymentName ./helm/$chartName -f $ctx.envValues -f $ctx.prValues $defaultConfigValues $prConfigValues $prCommands $extraCommands")
        Helm.writeUrlIfIngress(ctx, deploymentName)
      }
    }
  }

  static def undeployChart(ctx, environment, chartName, tag) {
    def deploymentName = "$chartName-$tag"
    ctx.echo("removing deployment $deploymentName")
    ctx.withKubeConfig([credentialsId: "kubeconfig-$environment"]) {
      ctx.sh("helm uninstall $deploymentName -n $deploymentName || echo error removing deployment $deploymentName")
      ctx.sh("kubectl delete namespaces $deploymentName || echo error removing namespace $deploymentName")
    }
  }

  static def publishChart(ctx, registry, chartName, tag) {
    ctx.withCredentials([
      ctx.usernamePassword(credentialsId: 'artifactory-credentials', usernameVariable: 'username', passwordVariable: 'password')
    ]) {
      // jenkins doesn't tidy up folder, remove old charts before running
      ctx.sh('rm -rf helm-charts')
      ctx.dir('helm-charts') {
        ctx.sh("sed -i -e 's/image: .*/image: $registry\\/$chartName:$tag/' ../helm/$chartName/values.yaml")
        Helm.addHelmRepo(ctx, 'ffc-public', ctx.HELM_CHART_REPO_PUBLIC)
        ctx.sh("helm package ../helm/$chartName --version $tag --dependency-update")
        ctx.sh("curl -u $ctx.username:$ctx.password -X PUT ${ctx.ARTIFACTORY_REPO_URL}ffc-helm-local/$chartName-${tag}.tgz -T $chartName-${tag}.tgz")
      }
    }
  }

  static def publishChartToACR(ctx, registry, chartName, tag, overwriteImagePath) {
    ctx.withEnv(['HELM_EXPERIMENTAL_OCI=1']) {
      ctx.withCredentials([
        ctx.usernamePassword(credentialsId: ctx.DOCKER_REGISTRY_CREDENTIALS_ID, usernameVariable: 'username', passwordVariable: 'password')
      ]) {
        // jenkins doesn't tidy up folder, remove old charts before running
        ctx.sh('rm -rf helm-charts')
        ctx.dir('helm-charts') {
          def helmChartName = "$registry/$chartName:helm-$tag"

          if (overwriteImagePath) {
            ctx.sh("sed -i -e 's/image: .*/image: $registry\\/$chartName:$tag/' ../helm/$chartName/values.yaml")
          }

          Helm.addHelmRepo(ctx, 'ffc-public', ctx.HELM_CHART_REPO_PUBLIC)
          ctx.sh("helm package ../helm/$chartName --version $tag --dependency-update")

          ctx.sh("helm registry login $registry --username $ctx.username --password $ctx.password")
          ctx.sh("helm chart save $chartName-${tag}.tgz $helmChartName")
          ctx.sh("helm chart push $helmChartName")

          ctx.deleteDir()
        }
      }
    }
  }

  static def deployRemoteChart(ctx, environment, namespace, chartName, chartVersion) {
    ctx.withKubeConfig([credentialsId: "kubeconfig-$environment"]) {
      ctx.withCredentials([
        ctx.file(credentialsId: "$chartName-$environment-values", variable: 'values')
      ]) {
        def extraCommands = Helm.getExtraCommands(chartVersion)
        Helm.addHelmRepo(ctx, 'ffc', "${ctx.ARTIFACTORY_REPO_URL}ffc-helm-virtual")
        ctx.sh("kubectl get namespaces $namespace || kubectl create namespace $namespace")
        ctx.sh("helm upgrade --namespace=$namespace $chartName -f $ctx.values --set namespace=$namespace ffc/$chartName $extraCommands")
      }
    }
  }

  static def deployRemoteChartFromACR(ctx, environment, namespace, chartName, chartVersion) {
    ctx.withEnv(['HELM_EXPERIMENTAL_OCI=1']) {
      ctx.withKubeConfig([credentialsId: "kubeconfig-$environment"]) {
        ctx.withCredentials([
          ctx.file(credentialsId: "$chartName-$environment-values", variable: 'values'),
          ctx.usernamePassword(credentialsId: ctx.DOCKER_REGISTRY_CREDENTIALS_ID, usernameVariable: 'username', passwordVariable: 'password')
        ]) {
          // jenkins doesn't tidy up folder, remove old charts before running
          ctx.sh('rm -rf helm-install')
          ctx.dir('helm-install') {
            def extraCommands = Helm.getExtraCommands(chartVersion)
            def helmChartName = "$ctx.DOCKER_REGISTRY/$chartName:helm-$chartVersion"

            ctx.sh("helm registry login $ctx.DOCKER_REGISTRY --username $ctx.username --password $ctx.password")
            ctx.sh("helm chart pull $helmChartName")
            ctx.sh("helm chart export $helmChartName --destination .")

            ctx.sh("kubectl get namespaces $namespace || kubectl create namespace $namespace")
            ctx.sh("helm upgrade $chartName $chartName --namespace=$namespace -f $ctx.values --set namespace=$namespace $extraCommands")

            ctx.deleteDir()
          }
        }
      }
    }
  }
}
