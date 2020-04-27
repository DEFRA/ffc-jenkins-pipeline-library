def call(Map config=[:], Closure body={}) {
  def containerSrcFolder = '\\/home\\/node'
  def localSrcFolder = '.'
  def lcovFile = './test-output/lcov.info'
  def sonarQubeEnv = 'SonarQube'
  def sonarScanner = 'SonarScanner'
  def qualityGateTimeout = 10
  repoName = ''
  pr = ''
  def containerTag = ''
  def mergedPrNo = ''

  node {
    checkout scm
    try {
      stage('Set GitHub status as pending') {
        build.setGithubStatusPending()
      }

      stage('Set PR, and containerTag variables') {
        (repoName, pr, containerTag, mergedPrNo) = build.getVariables(version.getPackageJsonVersion())
      }

      if (pr != '') {
        stage('Verify version incremented') {
          version.verifyPackageJsonIncremented()
        }
      }

      if (config.containsKey("validateClosure")) {
        echo "Here 1"
        echo "$pr"
        echo "Here 2"
        config["validateClosure"].call()
      }

      stage('Helm lint') {
        test.lintHelm(repoName)
      }

      stage('Build test image') {
        build.buildTestImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, BUILD_NUMBER)
      }

      if (config.containsKey("buildClosure")) {
        config["buildClosure"]()
      }

      stage('Run tests') {
        build.runTests(repoName, repoName, BUILD_NUMBER)
      }

      stage('Create JUnit report') {
        test.createReportJUnit()
      }

      stage('Fix lcov report') {
        utils.replaceInFile(containerSrcFolder, localSrcFolder, lcovFile)
      }

      stage('SonarQube analysis') {
        test.analyseCode(sonarQubeEnv, sonarScanner, test.buildCodeAnalysisDefaultParams(repoName))
      }

      stage("Code quality gate") {
        test.waitForQualityGateResult(qualityGateTimeout)
      }

      if (config.containsKey("testClosure")) {
        config["testClosure"]()
      }

      stage('Push container image') {
        build.buildAndPushContainerImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, containerTag)
      }

      if (pr != '') {
        stage('Helm install') {
          helm.deployChart(config.environment, DOCKER_REGISTRY, repoName, containerTag)
        }
      }
      else {
        stage('Publish chart') {
          helm.publishChart(DOCKER_REGISTRY, repoName, containerTag)
        }

        stage('Trigger GitHub release') {
          withCredentials([
            string(credentialsId: 'github-auth-token', variable: 'gitToken')
          ]) {
            release.trigger(containerTag, repoName, containerTag, gitToken)
          }
        }

        stage('Trigger Deployment') {
          withCredentials([
            string(credentialsId: "$repoName-deploy-token", variable: 'jenkinsToken')
          ]) {
            deploy.trigger(JENKINS_DEPLOY_SITE_ROOT, repoName, jenkinsToken, ['chartVersion': containerTag, 'environment': config.environment])
          }
        }
      }

      if (mergedPrNo != '') {
        stage('Remove merged PR') {
          helm.undeployChart(config.environment, repoName, mergedPrNo)
        }
      }

      if (config.containsKey("deployClosure")) {
        config["deployClosure"]()
      }

      stage('Set GitHub status as success'){
        build.setGithubStatusSuccess()
      }
    } catch(e) {
      stage('Set GitHub status as fail') {
        build.setGithubStatusFailure(e.message)
      }

      stage('Send build failure slack notification') {
        notifySlack.buildFailure(e.message, "#generalbuildfailures")
      }

      throw e
    } finally {
      stage('Delete output') {
        test.deleteOutput(repoName, containerSrcFolder)
      }
    }
  }
}
