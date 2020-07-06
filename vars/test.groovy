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

def analyseNodeJsCode(sonarQubeEnv, sonarScanner, repoName, branch, pr) {
  Tests.analyseNodeJsCode(this, sonarQubeEnv, sonarScanner, buildCodeAnalysisNodeJsParams(repoName, branch, pr))
}

def analyseDotNetCode(repoName, branch, pr) {
  Tests.analyseDotNetCode(this, buildCodeAnalysisDotNetParams(repoName, branch, pr))
}

def buildCodeAnalysisNodeJsParams(projectName, branch, pr) {
  return Tests.buildCodeAnalysisNodeJsParams(projectName, branch, pr)
}

def buildCodeAnalysisDotNetParams(projectName, branch, pr) {
  return Tests.buildCodeAnalysisDotNetParams(projectName, branch, pr)
}
