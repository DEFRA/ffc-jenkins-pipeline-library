import uk.gov.defra.ffc.Tests

def lintHelm(String chartName) {
  Tests.lintHelm(this, chartName)
}

def runGitHubSuperLinter(Boolean disableErrors=false) {
  Tests.runGitHubSuperLinter(this, disableErrors)
}

def createJUnitReport() {
  Tests.createJUnitReport(this)
}

def deleteOutput(String containerImage, String containerWorkDir) {
  Tests.deleteOutput(this, containerImage, containerWorkDir)
}

def analyseNodeJsCode(String sonarQubeEnv, String sonarScanner, String repoName, String branch, String defaultBranch, String pr) {
  Tests.analyseNodeJsCode(this, sonarQubeEnv, sonarScanner, buildCodeAnalysisNodeJsParams(repoName, branch, defaultBranch, pr))
}

def analyseDotNetCode(String repoName, String branch, String defaultBranch, String pr) {
  Tests.analyseDotNetCode(this, buildCodeAnalysisDotNetParams(repoName, branch, defaultBranch, pr))
}

def buildCodeAnalysisNodeJsParams(String projectName, String branch, String defaultBranch, String pr) {
  return Tests.buildCodeAnalysisNodeJsParams(projectName, branch, defaultBranch, pr)
}

def buildCodeAnalysisDotNetParams(String projectName, String branch, String defaultBranch, String pr) {
  return Tests.buildCodeAnalysisDotNetParams(projectName, branch, defaultBranch, pr)
}

def runAcceptanceTests(String pr, String environment, String repoName) {
  Tests.runAcceptanceTests(this, pr, environment, repoName)
}

def runZapScan(String projectName, String buildNumber, String tag) {
  Tests.runZapScan(this, projectName, buildNumber, tag)
}


def runPa11y(String projectName, String buildNumber, String tag) {
  Tests.runPa11y(this, projectName, buildNumber, tag)
}
