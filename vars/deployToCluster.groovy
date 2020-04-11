def call(Map config=[:], Closure body={}) {
  node {
    try {
      stage('Deploy Helm chart') {
        helm.deployRemoteChart(config.environment, config.namespace, config.chartName, config.chartVersion)
        throw new Exception()
      }      
      body()
    } catch(e) {
      notifySlack.deploymentFailure(e.message)
      throw e
    }
  }
}
