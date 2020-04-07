def repoName = ''
def branch = ''
def pr = ''
def mergedPrNo = ''
def containerTag = ''
def repoUrl = ''
def commitSha = ''
def containerSrcFolder = '\\/home\\/node'
def lcovFile = './test-output/lcov.info'
def localSrcFolder = '.'
def timeoutInMinutes = 10
def workspace

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
      /*stage('Helm lint') {
        lintHelm.lintHelm(repoName)
      }
      stage('Build test image') {
        buildTestImage.buildTestImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, BUILD_NUMBER)
      }
      stage('Run tests') {
        runTests.runTests(repoName, repoName, BUILD_NUMBER)
      }
      stage('Create JUnit report'){
        createTestReportJUnit.createTestReportJUnit()
      }
      stage('Fix lcov report') {
        replaceInFile.replaceInFile(containerSrcFolder, localSrcFolder, lcovFile)
      }
      stage('SonarQube analysis') {
        analyseCode.analyseCode(sonarQubeEnv, sonarScanner, ['sonar.projectKey' : repoName, 'sonar.sources' : '.'])
      }
      stage("Code quality gate") {
        waitForQualityGateResult.waitForQualityGateResult(timeoutInMinutes)
      }
      stage('Push container image') {
        buildAndPushContainerImage.buildAndPushContainerImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, containerTag)
      }
      if (pr != '') {
        stage('Verify version incremented') {
          verifyPackageJsonVersionIncremented.verifyPackageJsonVersionIncremented()
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
          publishChart.publishChart(DOCKER_REGISTRY, repoName, containerTag)
        }
        stage('Trigger GitHub release') {
          withCredentials([
            string(credentialsId: 'github-auth-token', variable: 'gitToken')
          ]) {
            triggerRelease.triggerRelease(containerTag, repoName, containerTag, gitToken)
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
          undeployChart.undeployChart(KUBE_CREDENTIALS_ID, repoName, mergedPrNo)
        }
      }*/
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