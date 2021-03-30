void call(Map config=[:], Closure body={}) {
  node {
    try {
      stage('Deploy Database') {
        database.runRemoteMigrations(config.environment, config.chartName, config.chartVersion)
      }
      stage('Deploy Helm chart') {
        helm.deployRemoteChart(config.environment, config.namespace, config.chartName, config.chartVersion, config.helmChartRepoType)
      }
      body()
    } catch(e) {
      notifySlack.deploymentFailure()
      throw e
    } finally {
      stage('Save console logs') {
        consoleLogs.save(JENKINS_DEPLOY_SITE_ROOT, config.chartName, BUILD_NUMBER, '/var/log/jenkins/console')
      }
    }
  }
}
