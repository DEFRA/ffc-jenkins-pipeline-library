import uk.gov.defra.ffc.Tests

def lintHelm(chartName) {
  Tests.lintHelm(this, chartName)
}

def createJUnitReport() {
  Tests.createJUnitReport(this)
}

def deleteOutput(containerImage, containerWorkDir) {
  Tests.deleteOutput(this, containerImage, containerWorkDir)
}

def analyseCode(sonarQubeEnv, sonarScanner, params) {
  Tests.analyseCode(this, sonarQubeEnv, sonarScanner, params)
}

def buildCodeAnalysisDefaultParams(projectName, branch, pr) {
  return Tests.buildCodeAnalysisDefaultParams(projectName, branch, pr)
}
