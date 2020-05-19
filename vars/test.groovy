import uk.gov.defra.ffc.Helm

def lintHelm(chartName) {
  Tests.lintHelm(this, chartName)
}

def createReportJUnit() {
  Tests.createReportJUnit(this)
}

def deleteOutput(containerImage, containerWorkDir) {
  Tests.deleteOutput(this, containerImage, containerWorkDir)
}

def analyseCode(sonarQubeEnv, sonarScanner, params) {
  Tests.analyseCode(this, sonarQubeEnv, sonarScanner, params)
}

def waitForQualityGateResult(timeoutInMinutes) {
  Tests.waitForQualityGateResult(this, timeoutInMinutes)
}
