// public
def lintHelm(chartName) {
  sh "helm lint ./helm/$chartName"
}

// public
def createTestReportJUnit(){
  junit 'test-output/junit.xml'
}

// public
def deleteTestOutput(containerImage, containerWorkDir) {
  // clean up files created by node/ubuntu user that cannot be deleted by jenkins. Note: uses global environment variable
  sh "[ -d \"$WORKSPACE/test-output\" ] && docker run --rm -u node --mount type=bind,source='$WORKSPACE/test-output',target=/$containerWorkDir/test-output $containerImage rm -rf test-output/*"
}

// public
def analyseCode(sonarQubeEnv, sonarScanner, params) {
  def scannerHome = tool sonarScanner
  withSonarQubeEnv(sonarQubeEnv) {
    def args = ''
    params.each { param ->
      args = args + " -D$param.key=$param.value"
    }

    sh "${scannerHome}/bin/sonar-scanner$args"
  }
}

// public
def waitForQualityGateResult(timeoutInMinutes) {
  timeout(time: timeoutInMinutes, unit: 'MINUTES') {
    def qualityGateResult = waitForQualityGate()
    if (qualityGateResult.status != 'OK') {
      error "Pipeline aborted due to quality gate failure: ${qualityGateResult.status}"
    }
  }
}
