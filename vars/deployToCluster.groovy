void call(Map config=[:], Closure body={}) {
  Boolean hasDatabase = true

  node {
    try {
      // stage('Deploy Database') {
      //   hasDatabase = database.runRemoteMigrations(config.environment, config.chartName, config.chartVersion)
      // }
      // stage('Deploy Helm chart') {
      //   helm.deployRemoteChart(config.environment, config.namespace, config.chartName, config.chartVersion, config.helmChartRepoType)
      // }
      stage('Trigger ADO pipelines') {
        ado.triggerPipeline(config.namespace, config.chartName, config.chartVersion, hasDatabase)
      }
      body()
    } catch(e) {
      notifySlack.deploymentFailure()
      throw e
    } finally {
      stage('Publish to Log Analytics') {
        consoleLogs.save('/var/log/jenkins/console')
      }
    }
  }
}
