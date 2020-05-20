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

  static def deployChart(ctx, environment, registry, chartName, tag) {
    ctx.withKubeConfig([credentialsId: "kubeconfig-$environment"]) {
      ctx.withCredentials([
        ctx.file(credentialsId: "$chartName-$environment-values", variable: 'envValues'),
        ctx.file(credentialsId: "$chartName-pr-values", variable: 'prValues')
      ]) {
        def deploymentName = "$chartName-$tag"
        def extraCommands = Helm.getExtraCommands(tag)
        def prCommands = Helm.getPrCommands(registry, chartName, tag, ctx.BUILD_NUMBER)
        ctx.sh("kubectl get namespaces $deploymentName || kubectl create namespace $deploymentName")
        ctx.sh("helm upgrade $deploymentName --namespace=$deploymentName ./helm/$chartName -f $ctx.envValues -f $ctx.prValues $prCommands $extraCommands")
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
}
