def call(Map config=[:], Closure body={}) {
  def containerSrcFolder = '\\/home\\/node'
  def localSrcFolder = '.'
  def lcovFile = './test-output/lcov.info'
  def sonarQubeEnv = 'SonarQube'
  def sonarScanner = 'SonarScanner'
  def qualityGateTimeout = 10
  def repoName = ''
  def pr = ''
  def containerTag = ''
  def mergedPrNo = ''

  node {
    checkout scm
    try {
      stage('Start') {
        echo "pipeline started"
      }
      stage('Set GitHub status as pending') {
        build.setGithubStatusPending()
      }
      stage('Set PR, and containerTag variables') {
        (repoName, pr, containerTag, mergedPrNo) = build.getVariables(version.getPackageJsonVersion())
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
      stage('SonarQube analysis') {
        test.analyseCode(sonarQubeEnv, sonarScanner, test.buildCodeAnalysisDefaultParams(repoName))
      }
      stage("Code quality gate") {
        test.waitForQualityGateResult(qualityGateTimeout)
      }
      stage('Push container image') {
        build.buildAndPushContainerImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, containerTag)
      }
      if (pr != '') {
        stage('Verify version incremented') {
          version.verifyPackageJsonIncremented()
        }
        stage('Helm install') {
          helm.deployChart(config.environment, DOCKER_REGISTRY, repoName, containerTag)
          echo "Build available for review at https://ffc-demo-$containerTag.$INGRESS_SERVER"
        }
      }
      if (pr == '') {
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
            string(credentialsId: 'web-deploy-job-name', variable: 'deployJobName'),
            string(credentialsId: 'web-deploy-token', variable: 'jenkinsToken')
          ]) {
            deploy.trigger(JENKINS_DEPLOY_SITE_ROOT, deployJobName, jenkinsToken, ['chartVersion': containerTag])
          }
        }
      }
      if (mergedPrNo != '') {
        stage('Remove merged PR') {
          helm.undeployChart(KUBE_CREDENTIALS_ID, repoName, mergedPrNo)
        }
      }
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
  body()
}