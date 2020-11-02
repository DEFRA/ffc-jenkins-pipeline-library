import uk.gov.defra.ffc.Tests

def lintHelm(chartName) {
  Tests.lintHelm(this, chartName)
}

def runGitHubSuperLinter(disableErrors=false) {
  Tests.runGitHubSuperLinter(this, disableErrors)
}

def createJUnitReport() {
  Tests.createJUnitReport(this)
}

def deleteOutput(containerImage, containerWorkDir) {
  Tests.deleteOutput(this, containerImage, containerWorkDir)
}

def analyseNodeJsCode(sonarQubeEnv, sonarScanner, repoName, branch, defaultBranch, pr) {
  Tests.analyseNodeJsCode(this, sonarQubeEnv, sonarScanner, buildCodeAnalysisNodeJsParams(repoName, branch, defaultBranch, pr))
}

def analyseDotNetCode(repoName, branch, defaultBranch, pr) {
  Tests.analyseDotNetCode(this, buildCodeAnalysisDotNetParams(repoName, branch, defaultBranch, pr))
}

def buildCodeAnalysisNodeJsParams(projectName, branch, defaultBranch, pr) {
  return Tests.buildCodeAnalysisNodeJsParams(projectName, branch, defaultBranch, pr)
}

def buildCodeAnalysisDotNetParams(projectName, branch, defaultBranch, pr) {
  return Tests.buildCodeAnalysisDotNetParams(projectName, branch, defaultBranch, pr)
}

def runAcceptanceTests(pr, environment, repoName) {
  Tests.runAcceptanceTests(this, pr, environment, repoName)
}

def runZapScan(projectName, buildNumber, tag) {
  Tests.runZapScan(this, projectName, buildNumber, tag)
}
