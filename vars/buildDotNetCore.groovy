def call(Map config=[:]) {
  def identityTag = ''
  def mergedPrNo = ''
  def pr = ''
  def repoName = ''

  node {
    checkout scm
    try {
      stage('Set GitHub status as pending') {
        build.setGithubStatusPending()
      }
      stage('Set PR, and identityTag variables') {
        (repoName, pr, identityTag, mergedPrNo) = build.getVariables(version.getCSProjVersion(config.project))
      }
      if (pr != '') {
        stage('Verify version incremented') {
          version.verifyCSProjIncremented(config.project)
        }
      }

      if (config.containsKey('validateClosure')) {
        config['validateClosure']()
      }

      stage('Helm lint') {
        test.lintHelm(repoName)
      }

      if (config.containsKey('buildClosure')) {
        config['buildClosure']()
      }

      stage('Build test image') {
        build.buildTestImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, BUILD_NUMBER, identityTag)
      }
      stage('Run tests') {
        build.runTests(repoName, repoName, BUILD_NUMBER, identityTag)
      }

      if (config.containsKey('testClosure')) {
        config['testClosure']()
      }

      stage('Push container image') {
        build.buildAndPushContainerImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, identityTag)
      }
      if (pr != '') {
        stage('Helm install') {
          helm.deployChart(config.environment, DOCKER_REGISTRY, repoName, identityTag)
        }
      }
      else {
        stage('Publish chart') {
          helm.publishChart(DOCKER_REGISTRY, repoName, identityTag, config.helmChartLocation)
        }
        stage('Trigger GitHub release') {
          withCredentials([
            string(credentialsId: 'github-auth-token', variable: 'gitToken')
          ]) {
            release.trigger(identityTag, repoName, identityTag, gitToken)
          }
        }
        stage('Trigger Deployment') {
          withCredentials([
            string(credentialsId: "$repoName-deploy-token", variable: 'jenkinsToken')
          ]) {
            deploy.trigger(JENKINS_DEPLOY_SITE_ROOT, repoName, jenkinsToken, ['chartVersion': identityTag])
          }
        }
      }

      if (config.containsKey('deployClosure')) {
        config['deployClosure']()
      }

      stage('Set GitHub status as success'){
        build.setGithubStatusSuccess()
      }
    } catch(e) {
      stage('Set GitHub status as fail') {
        build.setGithubStatusFailure(e.message)
      }

      stage('Send build failure slack notification') {
        notifySlack.buildFailure(e.message, '#generalbuildfailures')
      }

      if (config.containsKey('failureClosure')) {
        config['failureClosure']()
      }

      throw e
    } finally {
      if (config.containsKey('finallyClosure')) {
        config['finallyClosure']()
      }
    }
  }
}
