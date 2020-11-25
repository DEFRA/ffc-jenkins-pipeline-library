import uk.gov.defra.ffc.Tests

void lintHelm(String chartName) {
  Tests.lintHelm(this, chartName)
}

void runGitHubSuperLinter(Boolean disableErrors=false) {
  Tests.runGitHubSuperLinter(this, disableErrors)
}

void createJUnitReport() {
  Tests.createJUnitReport(this)
}

void deleteOutput(String containerImage, String containerWorkDir) {
  Tests.deleteOutput(this, containerImage, containerWorkDir)
}

void changeOwnershipOfWorkspace(String containerImage, String containerWorkDir) {
  Tests.changeOwnershipOfWorkspace(this, containerImage, containerWorkDir)
}

void analyseNodeJsCode(String sonarQubeEnv, String sonarScanner, String repoName, String branch, String defaultBranch, String pr) {
  Tests.analyseNodeJsCode(this, sonarQubeEnv, sonarScanner, buildCodeAnalysisNodeJsParams(repoName, branch, defaultBranch, pr))
}

void analyseDotNetCode(String repoName, String branch, String defaultBranch, String pr) {
  Tests.analyseDotNetCode(this, buildCodeAnalysisDotNetParams(repoName, branch, defaultBranch, pr))
}

def buildCodeAnalysisNodeJsParams(String projectName, String branch, String defaultBranch, String pr) {
  return Tests.buildCodeAnalysisNodeJsParams(projectName, branch, defaultBranch, pr)
}

def buildCodeAnalysisDotNetParams(String projectName, String branch, String defaultBranch, String pr) {
  return Tests.buildCodeAnalysisDotNetParams(projectName, branch, defaultBranch, pr)
}

void runAcceptanceTests(String pr, String environment, String repoName) {
  Tests.runAcceptanceTests(this, pr, environment, repoName)
}

void runZapScan(String projectName, String buildNumber, String tag) {
  Tests.runZapScan(this, projectName, buildNumber, tag)
}

void runPa11y(String projectName, String buildNumber, String tag) {
  Tests.runPa11y(this, projectName, buildNumber, tag)
}
