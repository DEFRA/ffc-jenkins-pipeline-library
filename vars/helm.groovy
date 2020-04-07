def getExtraCommands(chartName, tag) {
  return "--set labels.version=$tag"
}

// public
def deployChart(credentialsId, environment, registry, chartName, tag) {
  withKubeConfig([credentialsId: credentialsId]) {
    withCredentials([
      file(credentialsId: "$chartName-$environment-values", variable: 'envValues'),
      file(credentialsId: "$chartName-pr-values", variable: 'prValues')
    ]) {
      def deploymentName = "$chartName-$tag"
      def extraCommands = getExtraCommands(chartName, tag)
      sh "kubectl get namespaces $deploymentName || kubectl create namespace $deploymentName"
      sh "helm upgrade $deploymentName --namespace=$deploymentName --install --atomic ./helm/$chartName -f $envValues -f $prValues --set image=$registry/$chartName:$tag,namespace=$deploymentName,pr=$tag,name=$deploymentName,container.redeployOnChange=$tag-$BUILD_NUMBER $extraCommands"
      writeUrlIfIngress(deploymentName)
    }
  }
}

// private
def writeUrlIfIngress(deploymentName) {
  sh "if kubectl get ingress $deploymentName --ignore-not-found --namespace $deploymentName; then echo 'Build available for review at https://$deploymentName.$INGRESS_SERVER'; fi"
}

// public
def undeployChart(credentialsId, chartName, tag) {
  def deploymentName = "$chartName-$tag"
  echo "removing deployment $deploymentName"
  withKubeConfig([credentialsId: credentialsId]) {
    sh "helm uninstall $deploymentName || echo error removing deployment $deploymentName"
    sh "kubectl delete namespaces $deploymentName || echo error removing namespace $deploymentName"
  }
}

// public
def publishChart(registry, chartName, tag) {
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
def deployRemoteChart(credentialsId, environment, namespace, chartName, chartVersion) {
  withKubeConfig([credentialsId: credentialsId]) {
    withCredentials([
      file(credentialsId: "$chartName-$environment-values", variable: 'values')
    ]) {
      def extraCommands = getExtraCommands(chartName, chartVersion)
      sh "helm repo add ffc $HELM_CHART_REPO"
      sh "helm repo update"
      sh "kubectl get namespaces $namespace || kubectl create namespace $namespace"
      sh "helm upgrade --namespace=$namespace --install --atomic $chartName -f $values --set namespace=$namespace ffc/$chartName $extraCommands"
    }
  }
}
