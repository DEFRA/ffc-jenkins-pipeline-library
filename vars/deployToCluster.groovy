def call(Map config=[:], Closure body={}) {
  node {
    try {
      stage('Deploy Helm chart') {
        helm.deployRemoteChart(config.environment, config.namespace, config.chartName, config.chartVersion)
      }      
      body()
    } catch(e) {
      notifySlack.buildFailure(e.message, "#generalbuildfailures")
      throw e
    }
  }
}
