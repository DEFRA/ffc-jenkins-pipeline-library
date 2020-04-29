def call(Map config=[:], Closure body={}) {
  node {
    try {
      stage('Deploy Helm chart') {
        helm.deployHelmChart(config.environment, config.namespace, config.chartName, config.chartVersion)
      }
      body()
    } catch(e) {
      notifySlack.deploymentFailure(e.message)
      throw e
    }
  }
}
