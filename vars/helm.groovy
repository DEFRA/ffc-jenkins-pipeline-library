def getExtraCommands(tag) {
  return "--set labels.version=$tag --install --atomic"
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
  sh "kubectl get ingress -n $deploymentName -o json --ignore-not-found | jq '.items[0].spec.rules[0].host // empty' | xargs --no-run-if-empty printf 'Build available for review at https://%s\n'"
}

// public
def undeployChart(environment, chartName, tag) {
  def deploymentName = "$chartName-$tag"
  echo "removing deployment $deploymentName"
  withKubeConfig([credentialsId: "kubeconfig-$environment"]) {
    sh "helm uninstall $deploymentName || echo error removing deployment $deploymentName"
    sh "kubectl delete namespaces $deploymentName || echo error removing namespace $deploymentName"
  }
}

// public
def publishHelmChart(registry, chartName, tag) {
  withCredentials([
    string(credentialsId: 'helm-chart-repo', variable: 'helmRepo'),
    usernamePassword(credentialsId: 'artifactory-credentials', usernameVariable: 'username', passwordVariable: 'password')
  ]) {
    // jenkins doesn't tidy up folder, remove old charts before running
    sh "rm -rf helm-charts"
    dir('helm-charts') {
      sh "sed -i -e 's/image: .*/image: $registry\\/$chartName:$tag/' ../helm/$chartName/values.yaml"
      addHelmRepo('ffc-public', HELM_CHART_REPO_PUBLIC)
      sh "helm package ../helm/$chartName --version $tag --dependency-update"
      sh "curl -u $username:$password -X PUT ${ARTIFACTORY_HELM_REPO_URL}ffc-helm-local/$chartName-$tag.tgz -T $chartName-$tag.tgz"
    }
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
        addHelmRepo('ffc-public', HELM_CHART_REPO_PUBLIC)
        sh "helm package ../helm/$chartName --version $tag --dependency-update"
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
def deployHelmChart(environment, namespace, chartName, chartVersion) {
  withKubeConfig([credentialsId: "kubeconfig-$environment"]) {
    withCredentials([
      file(credentialsId: "$chartName-$environment-values", variable: 'values')
    ]) {
      def extraCommands = getExtraCommands(chartVersion)
      addHelmRepo('ffc', "${ARTIFACTORY_HELM_REPO_URL}ffc-helm-virtual/")
      sh "kubectl get namespaces $namespace || kubectl create namespace $namespace"
      sh "helm upgrade --namespace=$namespace $chartName -f $values --set namespace=$namespace ffc/$chartName $extraCommands"
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
      addHelmRepo('ffc', HELM_CHART_REPO)
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
