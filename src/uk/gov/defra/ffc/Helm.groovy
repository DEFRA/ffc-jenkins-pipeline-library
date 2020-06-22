package uk.gov.defra.ffc

class Helm implements Serializable {
  // static String suppressConsoleOutput = '#!/bin/bash +x\n'
  static String suppressConsoleOutput = ''

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

  static def escapeSpecialChars(str) {
    return str.replace('\\', '\\\\\\\\').replace(/,/, /\,/).replace(/"/, /\"/).replace(/`/, /\`/)
  }

  static def getValuesFromAppConfig(ctx, configKeys, prefix, label='\\\\0', failIfNotFound=true, delimiter='/') {
    def configItems = [:]

    configKeys.each { key ->
      def appConfigResults = ctx.sh(returnStdout: true, script:"$suppressConsoleOutput az appconfig kv list --subscription \$APP_CONFIG_SUBSCRIPTION --name \$APP_CONFIG_NAME --key $prefix$delimiter* --label $label --resolve-keyvault | jq '.[] | {.value'").trim()
      // def numResults = appConfigResults.tokenize('\n').size()

      // if (numResults == 1) {
      //     configItems[key.trim()] = $/"${Helm.escapeSpecialChars(appConfigResults)}"/$
      // }
      // else if (numResults == 0 && !failIfNotFound) { }
      // else {
      //     throw new Exception("Unexpected number of results from App Configuration when retrieving $prefix$delimiter$key: $numResults")
      // }
    }

    return configItems
  }

  static def configItemsToSetString(configItems) {
    return configItems.size() > 0 ? ("--set " + configItems.collect { "$it.key=$it.value" }.join(',')) : ''
  }

  static def getConfigKeysFromFile(ctx, filename) {
    return ctx.readFile(filename).tokenize('\n')
  }

  static def getConfig(ctx, helmKeys, label, prefix) {
    def appConfigResults = ctx.sh(returnStdout: true, script:"$suppressConsoleOutput az appconfig kv list --subscription \$APP_CONFIG_SUBSCRIPTION --name \$APP_CONFIG_NAME --key \"*\" --label=$label --resolve-keyvault | jq '. | map({ (.key): .value }) | add'").trim()
    def configMap = ctx.readJSON text: appConfigResults, returnPojo: true
    def results = [:]

    helmKeys.each { key ->
      def searchKey = prefix + key

      if (appConfigMap.containsKey(searchKey)) {
        results[key] = appConfigMap[searchKey]
      }
    }

    ctx.echo("$label, $prefix$key")
    results.each { key, value ->
      ctx.echo("$key => $value")
    }
  }

  static def deployChart(ctx, environment, registry, chartName, tag) {
    ctx.withKubeConfig([credentialsId: "kubeconfig-$environment"]) {
      def deploymentName = "$chartName-$tag"
      def extraCommands = Helm.getExtraCommands(tag)
      def prCommands = Helm.getPrCommands(registry, chartName, tag, ctx.BUILD_NUMBER)

      def configKeys = Helm.getConfigKeysFromFile(ctx, "helm/$chartName/$ctx.HELM_DEPLOYMENT_KEYS_FILENAME")

      getConfig(ctx, configKeys, '\\\\0', (environment + '/'))
      getConfig(ctx, configKeys, chartName, (environment + '/'))
      getConfig(ctx, configKeys, '\\\\0', (environment + '/pr/'))
      getConfig(ctx, configKeys, chartName, (environment + '/pr/'))

      // def items = Helm.getValuesFromAppConfig(ctx, configKeys, environment)
      // def defaultConfigValues = Helm.configItemsToSetString(Helm.getValuesFromAppConfig(ctx, configKeys, environment))
      // def defaultConfigValuesChart = Helm.configItemsToSetString(Helm.getValuesFromAppConfig(ctx, configKeys, environment, chartName, false))
      // def prConfigValues = Helm.configItemsToSetString(Helm.getValuesFromAppConfig(ctx, configKeys, "$environment/pr", '\\\\0', false))
      // def prConfigValuesChart = Helm.configItemsToSetString(Helm.getValuesFromAppConfig(ctx, configKeys, "$environment/pr", chartName, false))

      // ctx.sh("kubectl get namespaces $deploymentName || kubectl create namespace $deploymentName")
      // ctx.echo('Running helm upgrade, console output suppressed')
      // ctx.sh("$suppressConsoleOutput helm upgrade $deploymentName --namespace=$deploymentName ./helm/$chartName $defaultConfigValues $defaultConfigValuesChart $prConfigValues $prConfigValuesChart $prCommands $extraCommands")
      // ctx.sh("$suppressConsoleOutput helm upgrade $deploymentName --namespace=$deploymentName ./helm/$chartName $defaultConfigValues $prCommands $extraCommands")
      // Helm.writeUrlIfIngress(ctx, deploymentName)
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
          ctx.usernamePassword(credentialsId: ctx.DOCKER_REGISTRY_CREDENTIALS_ID, usernameVariable: 'username', passwordVariable: 'password')
        ]) {
          // jenkins doesn't tidy up folder, remove old charts before running
          ctx.sh('rm -rf helm-install')
          ctx.dir('helm-install') {
            def helmChartName = "$ctx.DOCKER_REGISTRY/$chartName:helm-$chartVersion"
            ctx.sh("helm registry login $ctx.DOCKER_REGISTRY --username $ctx.username --password $ctx.password")
            ctx.sh("helm chart pull $helmChartName")
            ctx.sh("helm chart export $helmChartName --destination .")

            def extraCommands = Helm.getExtraCommands(chartVersion)
            def configKeys = Helm.getConfigKeysFromFile(ctx, "$chartName/$ctx.HELM_DEPLOYMENT_KEYS_FILENAME")
            def defaultConfigValues = Helm.configItemsToSetString(Helm.getValuesFromAppConfig(ctx, configKeys, environment))

            ctx.sh("kubectl get namespaces $namespace || kubectl create namespace $namespace")
            ctx.echo('Running helm upgrade, console output suppressed')
            ctx.sh("$suppressConsoleOutput helm upgrade $chartName $chartName --namespace=$namespace $defaultConfigValues --set namespace=$namespace $extraCommands")

            ctx.deleteDir()
          }
        }
      }
    }
  }
}
