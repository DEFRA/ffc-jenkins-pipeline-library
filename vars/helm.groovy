def getExtraCommands(tag) {
  return "--set labels.version=$tag --install --atomic"
}

def getPrCommands(registry, chartName, tag) {
  def helmValues = [
    /image=$registry\/$chartName:$tag/,
    /namespace=$chartName-$tag/,
    /pr=$tag/,
    /name=$chartName-$tag/,
    /container.redeployOnChange=$tag-$BUILD_NUMBER/    
    ].join(',')

    return "--set $helmValues"
}

// public
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
      writeUrlIfIngress(deploymentName)
    }
  }
}

// private
def writeUrlIfIngress(deploymentName) {  
  sh "if kubectl get ingress $deploymentName --ignore-not-found --namespace $deploymentName; then echo 'Build available for review at https://$deploymentName.$INGRESS_SERVER'; fi"
}

// public
def undeployChart(credentialsId, tag) {
  def chartName = build.getRepoName()
  def deploymentName = "$chartName-$tag"
  echo "removing deployment $deploymentName"
  withKubeConfig([credentialsId: credentialsId]) {
    sh "helm uninstall $deploymentName || echo error removing deployment $deploymentName"
    sh "kubectl delete namespaces $deploymentName || echo error removing namespace $deploymentName"
  }
}

// public
def publishChart(registry, tag) {
  def chartName = build.getRepoName()
  withCredentials([
    string(credentialsId: 'helm-chart-repo', variable: 'helmRepo')
  ]) {
    // jenkins doesn't tidy up folder, remove old charts before running
    sh "rm -rf helm-charts"
    sshagent(credentials: ['helm-chart-creds']) {
      sh "git clone $helmRepo"
      dir('helm-charts') {
        sh "sed -i -e 's/image: .*/image: $registry\\/$chartName:$tag/' ../helm/$chartName/values.yaml"
        sh "sed -i -e 's/version:.*/version: $tag/' ../helm/$chartName/Chart.yaml"
        sh "helm package ../helm/$chartName"
        sh 'helm repo index .'
        sh 'git config --global user.email "buildserver@defra.gov.uk"'
        sh 'git config --global user.name "buildserver"'
        sh 'git checkout master'
        sh 'git add -A'
        sh "git commit -m 'update $chartName helm chart from build job'"
        sh 'git push'
      }
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
      sh "helm repo add ffc $HELM_CHART_REPO"
      sh "helm repo update"
      sh "kubectl get namespaces $namespace || kubectl create namespace $namespace"
      sh "helm upgrade --namespace=$namespace $chartName -f $values --set namespace=$namespace ffc/$chartName $extraCommands"
    }
  }
}
