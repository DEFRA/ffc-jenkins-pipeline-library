def getExtraCommands(tag) {
  return "--set labels.version=$tag --install --atomic --version=$tag"
}

def getPrCommands(registry, chartName, tag) {
  def helmValues = [
    /image=$registry\/$chartName:$tag/,
    /namespace=$chartName-$tag/,
    /pr=$tag/,
    /deployment.redeployOnChange=$tag-$BUILD_NUMBER/
    ].join(',')

    return "--set $helmValues"
}

def deployChart(environment, registry, chartName, tag) {
  withKubeConfig([credentialsId: "kubeconfig-$environment"]) {
    withCredentials([
      file(credentialsId: "$chartName-$environment-values", variable: 'envValues'),
      file(credentialsId: "$chartName-pr-values", variable: 'prValues')
    ]) {
      def deploymentName = "$chartName-$tag"
      def extraCommands = getExtraCommands(tag)
      def prCommands = getPrCommands(registry, chartName, tag)
      sh "kubectl get namespaces $deploymentName || kubectl create namespace $deploymentName"
      sh "helm upgrade $deploymentName --namespace=$deploymentName ./helm/$chartName -f $envValues -f $prValues $prCommands $extraCommands"
      Helm.writeUrlIfIngress(this, deploymentName)
    }
  }
}

// public
def undeployChart(environment, chartName, tag) {
  def deploymentName = "$chartName-$tag"
  echo "removing deployment $deploymentName"
  withKubeConfig([credentialsId: "kubeconfig-$environment"]) {
    sh "helm uninstall $deploymentName -n $deploymentName || echo error removing deployment $deploymentName"
    sh "kubectl delete namespaces $deploymentName || echo error removing namespace $deploymentName"
  }
}

// public
def publishChart(registry, chartName, tag) {
  withCredentials([
    usernamePassword(credentialsId: 'artifactory-credentials', usernameVariable: 'username', passwordVariable: 'password')
  ]) {
    // jenkins doesn't tidy up folder, remove old charts before running
    sh "rm -rf helm-charts"
    dir('helm-charts') {
      sh "sed -i -e 's/image: .*/image: $registry\\/$chartName:$tag/' ../helm/$chartName/values.yaml"
      addHelmRepo('ffc-public', HELM_CHART_REPO_PUBLIC)
      sh "helm package ../helm/$chartName --version $tag --dependency-update"
      sh "curl -u $username:$password -X PUT ${ARTIFACTORY_REPO_URL}ffc-helm-local/$chartName-${tag}.tgz -T $chartName-${tag}.tgz"
    }
  }
}

// public
def deployRemoteChart(environment, namespace, chartName, chartVersion) {
  withKubeConfig([credentialsId: "kubeconfig-$environment"]) {
    withCredentials([
      file(credentialsId: "$chartName-$environment-values", variable: 'values')
    ]) {
      def extraCommands = getExtraCommands(chartVersion)
      addHelmRepo('ffc', "${ARTIFACTORY_REPO_URL}ffc-helm-virtual")
      sh "kubectl get namespaces $namespace || kubectl create namespace $namespace"
      sh "helm upgrade --namespace=$namespace $chartName -f $values --set namespace=$namespace ffc/$chartName $extraCommands"
    }
  }
}

// private
def addHelmRepo(repoName, url) {
  sh "helm repo add $repoName $url"
  sh "helm repo update"
}
