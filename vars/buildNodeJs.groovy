
/* def setGithubStatusPending = load 'setGithubStatusPending'
def setGithubStatusSuccess = 'setGithubStatusSuccess'
def setGithubStatusFailure = 'setGithubStatusFailure'
def getVariables = load 'getVariables.groovy'
def getPackageJsonVersion = load 'getPackageJsonVersion'
def lintHelm = load 'lintHelm'
def buildTestImage = load 'buildTestImage'
def runTests = load 'runTests'
def createTestReportJUnit = load 'createTestReportJUnit'
def replaceInFile = load 'replaceInFile'
def sonarQubeEnv = 'SonarQube'
def sonarScanner = 'SonarScanner'
def analyseCode = 'analyseCode'
def waitForQualityGateResult = 'waitForQualityGateResult'
def buildAndPushContainerImage = 'buildAndPushContainerImage'
def notifySlackBuildFailure = 'notifySlackBuildFailure'
def deleteTestOutput = 'deleteTestOutput'
def verifyPackageJsonVersionIncremented = 'verifyPackageJsonVersionIncremented'
def undeployChart = 'undeployChart'
def publishChart = 'publishChart'
def triggerRelease = 'triggerRelease' */

def call(Map config=[:], Closure body={}) {
  def containerSrcFolder = '\\/home\\/node'
  def localSrcFolder = '.'
  def lcovFile = './test-output/lcov.info'
  def sonarQubeEnv = 'SonarQube'
  def sonarScanner = 'SonarScanner'
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
      stage('Set GitHub status as pending'){
        build.setGithubStatusPending()
      }
      stage('Set PR, and containerTag variables') {
        (repoName, pr, containerTag, mergedPrNo) = build.getVariables(version.getPackageJsonVersion())
      }
      stage('Helm lint') {
        test.lintHelm()
      }
      stage('Build test image') {
        build.buildTestImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, BUILD_NUMBER)
      }
      stage('Run tests') {
        build.runTests(BUILD_NUMBER)
      }
      stage('Create JUnit report'){
        test.createReportJUnit()
      }
      stage('Fix lcov report') {
        utils.replaceInFile(containerSrcFolder, localSrcFolder, lcovFile)
      }
      stage('SonarQube analysis') {
        test.analyseCode(sonarQubeEnv, sonarScanner)
      }
      stage("Code quality gate") {
        test.waitForQualityGateResult(10)
      }
      stage('Push container image') {
        build.buildAndPushContainerImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, containerTag)
      }
      if (pr != '') {
        stage('Verify version incremented') {
          version.verifyPackageJsonIncremented()
        }
      //   stage('Helm install') {
      //     withCredentials([
      //         string(credentialsId: 'web-alb-tags', variable: 'albTags'),
      //         string(credentialsId: 'web-alb-security-groups', variable: 'albSecurityGroups'),
      //         string(credentialsId: 'web-alb-arn', variable: 'albArn'),
      //         string(credentialsId: 'web-cookie-password', variable: 'cookiePassword')
      //       ]) {

      //       def helmValues = [
      //         /container.redeployOnChange="$pr-$BUILD_NUMBER"/,
      //         /container.redisHostname="$REDIS_HOSTNAME"/,
      //         /container.redisPartition="ffc-demo-$containerTag"/,
      //         /cookiePassword="$cookiePassword"/,
      //         /ingress.alb.tags="$albTags"/,
      //         /ingress.alb.arn="$albArn"/,
      //         /ingress.alb.securityGroups="$albSecurityGroups"/,
      //         /ingress.endpoint="ffc-demo-$containerTag"/,
      //         /name="ffc-demo-$containerTag"/,
      //         /labels.version="$containerTag"/
      //       ].join(',')

      //       def extraCommands = [
      //         "--values ./helm/$serviceName/jenkins-aws.yaml",
      //         "--set $helmValues"
      //       ].join(' ')

      //       defraUtils.deployChart(KUBE_CREDENTIALS_ID, DOCKER_REGISTRY, serviceName, containerTag, extraCommands)
      //       echo "Build available for review at https://ffc-demo-$containerTag.$INGRESS_SERVER"
      //     }
      //   }
      }
      if (pr == '') {
        stage('Publish chart') {
          helm.publishChart(DOCKER_REGISTRY, containerTag)
        }
        stage('Trigger GitHub release') {
          withCredentials([
            string(credentialsId: 'github-auth-token', variable: 'gitToken')
          ]) {
            release.trigger(containerTag, containerTag, gitToken)
          }
        }
      //   stage('Trigger Deployment') {
      //     withCredentials([
      //       string(credentialsId: 'web-deploy-job-name', variable: 'deployJobName'),
      //       string(credentialsId: 'web-deploy-token', variable: 'jenkinsToken')
      //     ]) {
      //       defraUtils.triggerDeploy(JENKINS_DEPLOY_SITE_ROOT, deployJobName, jenkinsToken, ['chartVersion': containerTag])
      //     }
      //   }
      // }
      if (mergedPrNo != '') {
        stage('Remove merged PR') {
          helm.undeployChart(KUBE_CREDENTIALS_ID, mergedPrNo)
        }
      }
      stage('Set GitHub status as success'){
        build.setGithubStatusSuccess()
      }
    } catch(e) {
      build.setGithubStatusFailure(e.message)
      // notifySlackBuildFailure.notifySlackBuildFailure(e.message, "#generalbuildfailures")
     throw e
    } finally {
      // deleteTestOutput.deleteTestOutput(repoName, containerSrcFolder)
    }
  }
  body()
}