def call(Map config=[:]) {
  def containerSrcFolder = '\\/home\\/node'
  def localSrcFolder = '.'
  def lcovFile = './test-output/lcov.info'
  def repoName = ''
  def pr = ''
  def tag = ''
  def mergedPrNo = ''

  node {
    checkout scm
    try {
      stage('Set GitHub status as pending') {
        build.setGithubStatusPending()
      }

      stage('Set PR, and tag variables') {
        (repoName, pr, tag, mergedPrNo) = build.getVariables(version.getPackageJsonVersion())
      }

      if (pr != '') {
        stage('Verify version incremented') {
          version.verifyPackageJsonIncremented()
        }
      }

      if (config.containsKey('validateClosure')) {
        config['validateClosure']()
      }

      stage('Helm lint') {
        test.lintHelm(repoName)
      }

      stage('npm audit') {
        build.npmAudit(config.npmAuditLevel, config.npmAuditLogType, config.npmAuditFailOnIssues)
      }

      stage('Snyk test') {
        build.snykTest(config.snykFailOnIssues, config.snykOrganisation, config.snykSeverity)
      }

      stage('Build test image') {
        build.buildTestImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, BUILD_NUMBER, tag)
      }

      if (config.containsKey('buildClosure')) {
        config['buildClosure']()
      }

      stage('Run tests') {
        build.runTests(repoName, repoName, BUILD_NUMBER, tag)
      }

      stage('Create JUnit report') {
        test.createJUnitReport()
      }

      stage('Fix lcov report') {
        utils.replaceInFile(containerSrcFolder, localSrcFolder, lcovFile)
      }

      if (config.containsKey('testClosure')) {
        config['testClosure']()
      }

      stage('Push container image') {
        build.buildAndPushContainerImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, tag)
      }

      if (pr != '') {
        stage('Helm install') {
          helm.deployChart(config.environment, DOCKER_REGISTRY, repoName, tag)
        }
      }
      else {
        stage('Publish chart') {
          helm.publishChart(DOCKER_REGISTRY, repoName, tag, HELM_CHART_REPO_TYPE)
        }

        stage('Trigger GitHub release') {
          withCredentials([
            string(credentialsId: 'github-auth-token', variable: 'gitToken')
          ]) {
            release.trigger(tag, repoName, tag, gitToken)
          }
        }

        stage('Trigger Deployment') {
          withCredentials([
            string(credentialsId: "$repoName-deploy-token", variable: 'jenkinsToken')
          ]) {
            deploy.trigger(JENKINS_DEPLOY_SITE_ROOT, repoName, jenkinsToken, ['chartVersion': tag, 'environment': config.environment, 'helmChartRepoType': HELM_CHART_REPO_TYPE])
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
      echo("Build failed with message: $e.message")

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
      stage('Clean up test output') {
        test.deleteOutput('defradigital/node-development', containerSrcFolder)
      }

      if (config.containsKey('finallyClosure')) {
        config['finallyClosure']()
      }
    }
  }
}
