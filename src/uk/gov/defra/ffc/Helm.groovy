package uk.gov.defra.ffc

import uk.gov.defra.ffc.GitHubStatus
import uk.gov.defra.ffc.Utils

class Helm implements Serializable {

  static def writeUrlIfIngress(ctx, deploymentName) {
    ctx.sh("kubectl get ingress -n $deploymentName -o json --ignore-not-found | jq '.items[0].spec.rules[0].host // empty' | xargs --no-run-if-empty printf 'Build available for review at https://%s\n'")
  }

  static def addHelmRepo(ctx, repoName, url) {
    ctx.sh("helm repo add --force-update $repoName $url")
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

  static def deployChart(ctx, environment, registry, chartName, tag, pr) {
    ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.DeployChart.Context, description: GitHubStatus.DeployChart.Description) {
      ctx.withKubeConfig([credentialsId: "kubeconfig-$environment"]) {
        String helmValuesFilePath = "helm/$chartName/values.yaml"
        def deploymentName = "$chartName-$tag"
        def extraCommands = getExtraCommands(tag)
        def prCommands = getPrCommands(registry, chartName, tag, ctx.BUILD_NUMBER)

        // read Helm values file to get list of keys to match in Azure Application Configuration
        def helmValuesKeys = getHelmValuesKeys(ctx, helmValuesFilePath)

        // first get all common keys from Azure Applicaiton Configuration matching Helm values
        String commonPrefix = 'common/'
        def commonConfigValues = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, commonPrefix))
        def commonConfigValuesChart = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, commonPrefix, chartName))

        // next get all environment specific keys from Azure Applicaiton Configuration matching Helm values
        String environmentPrefix = environment + '/'
        def environmentConfigValues = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, environmentPrefix))
        def environmentConfigValuesChart = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, environmentPrefix, chartName))

        // next get all service specific keys from Azure Applicaiton Configuration if the values file includes a workstream property
        String serviceName = ctx.sh(returnStdout: true, script: "yq r $helmValuesFilePath workstream").trim()
        def serviceCommonConfigValues
        def serviceCommonConfigValuesChart
        def serviceEnvironmentConfigValues
        def serviceEnvironmentConfigValuesChart

        if(serviceName != '') {
          // get common values for a service
          String serviceCommonPrefix = serviceName + '/common/'
          serviceCommonConfigValues = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, serviceCommonPrefix))
          serviceCommonConfigValuesChart = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, serviceCommonPrefix, chartName))

          // get environment values for a service
          String serviceEnvironmentPrefix = serviceName + '/' + environment + '/'
          serviceEnvironmentConfigValues = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, serviceEnvironmentPrefix))
          serviceEnvironmentConfigValuesChart = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, serviceEnvironmentPrefix, chartName))
        }

        // next get all pr specific values
        String prConfigPrefix = 'pr/'
        def prConfigValues = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, prConfigPrefix))
        def prConfigValuesChart = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, prConfigPrefix, chartName))

        // finally get all dynamically provisioned values
        def prProvisionedValues = configItemsToSetString(
          Provision.getProvisionedQueueConfigValues(ctx, chartName, pr) +
          Provision.getProvisionedDbSchemaConfigValues(ctx, chartName, pr)
        )

        ctx.sh("kubectl get namespaces $deploymentName || kubectl create namespace $deploymentName")
        ctx.echo('Running helm upgrade, console output suppressed')
        ctx.sh("$Utils.suppressConsoleOutput helm upgrade $deploymentName --namespace=$deploymentName ./helm/$chartName $commonConfigValues $commonConfigValuesChart $environmentConfigValues $environmentConfigValuesChart $serviceCommonConfigValues $serviceCommonConfigValuesChart $serviceEnvironmentConfigValues $serviceEnvironmentConfigValuesChart $prConfigValues $prConfigValuesChart $prProvisionedValues $prCommands $extraCommands")
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
          // ctx.sh("helm package $chartName-${tag}.tgz $helmChartName")
          def script = "helm push $chartName-$tag​.tgz $helmChartName"
          ctx.echo(script)
          ctx.sh(script)


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
            ctx.sh("helm pull $helmChartName --destination .")
            String helmValuesFilePath = "$chartName/values.yaml"

            def extraCommands = getExtraCommands(chartVersion)

            def helmValuesKeys = getHelmValuesKeys(ctx, helmValuesFilePath)

            // first get all common keys from Azure Applicaiton Configuration matching Helm values
            String commonPrefix = 'common/'
            def commonConfigValues = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, commonPrefix))
            def commonConfigValuesChart = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, commonPrefix, chartName))

            // next get all environment specific keys from Azure Applicaiton Configuration matching Helm values
            String environmentPrefix = environment + '/'
            def environmentConfigValues = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, environmentPrefix))
            def environmentConfigValuesChart = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, environmentPrefix, chartName))

            // next get all service specific keys from Azure Applicaiton Configuration if the values file includes a workstream property
            String serviceName = ctx.sh(returnStdout: true, script: "yq r $helmValuesFilePath workstream").trim()
            def serviceCommonConfigValues
            def serviceCommonConfigValuesChart
            def serviceEnvironmentConfigValues
            def serviceEnvironmentConfigValuesChart

            if(serviceName != '') {
              // get common values for a service
              String serviceCommonPrefix = serviceName + '/common/'
              serviceCommonConfigValues = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, serviceCommonPrefix))
              serviceCommonConfigValuesChart = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, serviceCommonPrefix, chartName))

              // get environment values for a service
              String serviceEnvironmentPrefix = serviceName + '/' + environment + '/'
              serviceEnvironmentConfigValues = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, serviceEnvironmentPrefix))
              serviceEnvironmentConfigValuesChart = configItemsToSetString(Utils.getConfigValues(ctx, helmValuesKeys, serviceEnvironmentPrefix, chartName))
            }

            ctx.sh("kubectl get namespaces $namespace || kubectl create namespace $namespace")
            ctx.echo('Running helm upgrade, console output suppressed')
            ctx.sh("$Utils.suppressConsoleOutput helm upgrade $chartName $chartName --namespace=$namespace $commonConfigValues $commonConfigValuesChart $environmentConfigValues $environmentConfigValuesChart $serviceCommonConfigValues $serviceCommonConfigValuesChart $serviceEnvironmentConfigValues $serviceEnvironmentConfigValuesChart --set namespace=$namespace $extraCommands")

            ctx.deleteDir()
          }
        }
      }
    }
  }
}
