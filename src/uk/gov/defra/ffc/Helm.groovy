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

  static def configItemsToSetString(configItems) {
    return configItems.size() > 0 ? ("--set " + configItems.collect { "$it.key=$it.value" }.join(',')) : ''
  }

  static def getHelmValuesKeys(ctx, helmValuesFileLocation) {
    def helmValuesKeys = ctx.sh(returnStdout: true, script:"yq r $helmValuesFileLocation --printMode p \"**\"").trim()
    // yq outputs arrays elements as .[ but the --set syntax for the helm command doesn't use the dot so remove it
    return helmValuesKeys.tokenize('\n').collect { it.replace('.[', '[').trim() }
  }

  static def deployChart(ctx, environment, registry, chartName, tag) {
    ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.DeployChart.Context, description: GitHubStatus.DeployChart.Description) {
      ctx.withKubeConfig([credentialsId: "kubeconfig-$environment"]) {
        def deploymentName = "$chartName-$tag"
        def extraCommands = getExtraCommands(tag)
        def prCommands = getPrCommands(registry, chartName, tag, ctx.BUILD_NUMBER)

        def helmValuesKeys = getHelmValuesKeys(ctx, "helm/$chartName/values.yaml")
        def appConfigPrefix = environment + '/'
        def defaultConfigValues = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, appConfigPrefix))
        def defaultConfigValuesChart = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, appConfigPrefix, chartName))
        def prConfigValues = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, (appConfigPrefix + 'pr/')))
        def prConfigValuesChart = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, (appConfigPrefix + 'pr/'), chartName))
        ctx.echo("PRCONFIGVALUES: $prConfigValues")

        // TODO: Set PR specific provisioned resources
        def prProvisionedValues = configItemsToSetString([ 'container.claimQueueAddress': 'ffc-demo-claim-service-pr136-claim',  'container.scheduleQueueAddress': 'ffc-demo-claim-service-pr136-schedule', 'container.calculationQueueAddress': 'ffc-demo-claim-service-pr136-calculation' ])
        ctx.echo("PRPOVISIONEDVALUES: $prProvisionedValues")

        ctx.sh("kubectl get namespaces $deploymentName || kubectl create namespace $deploymentName")
        ctx.echo('Running helm upgrade, console output suppressed')
        ctx.sh("$Utils.suppressConsoleOutput helm upgrade $deploymentName --namespace=$deploymentName ./helm/$chartName $defaultConfigValues $defaultConfigValuesChart $prConfigValues $prConfigValuesChart $prProvisionedValues $prCommands $extraCommands")
        writeUrlIfIngress(ctx, deploymentName)
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
        addHelmRepo(ctx, 'ffc-public', ctx.HELM_CHART_REPO_PUBLIC)
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

          addHelmRepo(ctx, 'ffc-public', ctx.HELM_CHART_REPO_PUBLIC)
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
        def extraCommands = getExtraCommands(chartVersion)
        addHelmRepo(ctx, 'ffc', "${ctx.ARTIFACTORY_REPO_URL}ffc-helm-virtual")
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

            def extraCommands = getExtraCommands(chartVersion)

            def helmValuesKeys = getHelmValuesKeys(ctx, "$chartName/values.yaml")
            def appConfigPrefix = environment + '/'
            def defaultConfigValues = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, appConfigPrefix))
            def defaultConfigValuesChart = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, appConfigPrefix, chartName))

            ctx.sh("kubectl get namespaces $namespace || kubectl create namespace $namespace")
            ctx.echo('Running helm upgrade, console output suppressed')
            ctx.sh("$Utils.suppressConsoleOutput helm upgrade $chartName $chartName --namespace=$namespace $defaultConfigValues $defaultConfigValuesChart --set namespace=$namespace $extraCommands")

            ctx.deleteDir()
          }
        }
      }
    }
  }
}
