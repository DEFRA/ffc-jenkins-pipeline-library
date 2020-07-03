def call(Map config=[:]) {
  def containerSrcFolder = '\\/home\\/dotnet'
  def localSrcFolder = '\\/home\\/dotnet\\/working/project'
  def coverageFile = './test-output/coverage.opencover.xml'
  def developmentImage = 'defradigital/dotnetcore-development'
  def tag = ''
  def mergedPrNo = ''
  def pr = ''
  def repoName = ''

  node {
    checkout scm
    try {
      stage('Set GitHub status as pending') {
        build.setGithubStatusPending()
      }
      stage('Set PR, and tag variables') {
        (repoName, pr, tag, mergedPrNo) = build.getVariables(version.getCSProjVersion(config.project))
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
        build.buildTestImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, BUILD_NUMBER, tag)
      }

      stage('Run tests') {
        build.runTests(repoName, repoName, BUILD_NUMBER, tag)
      }

      stage('Fix coverage report') {
        utils.replaceInFile(localSrcFolder, containerSrcFolder, coverageFile)
      }

      stage('SonarCloud analysis') {
        test.analyseDotNetCode(test.buildCodeAnalysisDotNetParams(repoName, BRANCH_NAME, pr))        
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
            deploy.trigger(JENKINS_DEPLOY_SITE_ROOT, repoName, jenkinsToken, ['chartVersion': tag, 'helmChartRepoType': HELM_CHART_REPO_TYPE])
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
        test.deleteOutput(developmentImage, containerSrcFolder)
      }

      if (config.containsKey('finallyClosure')) {
        config['finallyClosure']()
      }
    }
  }
}
