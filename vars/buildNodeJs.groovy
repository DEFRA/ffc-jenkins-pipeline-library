def call(Map config=[:], Closure body={}) {
  def containerSrcFolder = '\\/home\\/node'
  def localSrcFolder = '.'
  def lcovFile = './test-output/lcov.info'
  def qualityGateTimeout = 10
  def repoName = ''
  def pr = ''
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
      stage('Helm lint') {
        test.lintHelm(repoName)
      }
      stage('Build test image') {
        build.buildTestImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, BUILD_NUMBER)
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
      body()
      stage('Set GitHub status as success'){
        build.setGithubStatusSuccess()
      }
    } catch(e) {
      build.setGithubStatusFailure(e.message)
      notifySlack.buildFailure(e.message, "#generalbuildfailures")
      throw e
    } finally {
      test.deleteOutput(repoName, containerSrcFolder)
    }
  }
}
