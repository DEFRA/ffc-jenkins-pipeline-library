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

void changeOwnershipOfWorkspace(String containerImage, String containerWorkDir) {
  Tests.changeOwnershipOfWorkspace(this, containerImage, containerWorkDir)
}

void analyseNodeJsCode(String sonarQubeEnv, String sonarScanner, String repoName, String branch, String defaultBranch, String pr) {
  Tests.analyseNodeJsCode(this, sonarQubeEnv, sonarScanner, buildCodeAnalysisParams(repoName, branch, defaultBranch, pr))
}

void analyseDotNetCode(String repoName, String projectName, String branch, String defaultBranch, String pr) {
  Tests.analyseDotNetCode(this, projectName, buildCodeAnalysisParams(repoName, branch, defaultBranch, pr))
}

def buildCodeAnalysisParams(String projectName, String branch, String defaultBranch, String pr) {
  return Tests.buildCodeAnalysisParams(projectName, branch, defaultBranch, pr)
}

void runAcceptanceTests(String pr, String environment, String repoName) {
  Tests.runAcceptanceTests(this, pr, environment, repoName)
}

void runServiceAcceptanceTests(String projectName, String serviceName, String buildNumber, String tag, String pr) {
  Tests.runServiceAcceptanceTests(this, projectName, serviceName, buildNumber, tag, pr)
}

void runJmeterTests(String pr, String environment, String repoName) {
  Tests.runJmeterTests(this, pr, environment, repoName)
}

void runZapScan(String projectName, String buildNumber, String tag) {
  Tests.runZapScan(this, projectName, buildNumber, tag)
}

void runAccessibilityTests(String projectName, String buildNumber, String tag, String accessibilityTestType) {
  Tests.runAccessibilityTests(this, projectName, buildNumber, tag, accessibilityTestType)
}
