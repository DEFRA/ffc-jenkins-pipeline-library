import uk.gov.defra.ffc.Build
import uk.gov.defra.ffc.Docker
import uk.gov.defra.ffc.Tests

def getVariables(String version, String defaultBranch) {
  return Build.getVariables(this, version, defaultBranch)
}

def getDefaultBranch(String defaultBranch, String requestedBranch) {
  return Build.getDefaultBranch(defaultBranch, requestedBranch)
}

def checkoutSourceCode(String defaultBranch) {
  return Build.checkoutSourceCode(this, defaultBranch)
}

def buildTestImage(String credentialsId, String registry, String projectName, String buildNumber, String tag) {
  Docker.buildTestImage(this, credentialsId, registry, projectName, buildNumber, tag)
}

def runTests(String projectName, String serviceName, String buildNumber, String tag, int pr, String environment) {
  Tests.runTests(this, projectName, serviceName, buildNumber, tag, pr, environment)
}

def buildAndPushContainerImage(String credentialsId, String registry, String imageName, String tag) {
  Docker.buildAndPushContainerImage(this, credentialsId, registry, imageName, tag)
}

def npmAudit(String auditLevel, String logType, String failOnIssues, String containerImage, String containerWorkDir, String pr) {
  Build.npmAudit(this, auditLevel, logType, failOnIssues, containerImage, containerWorkDir, pr)
}

def extractSynkFiles(String projectName, String buildNumber, String tag) {
  Build.extractSynkFiles(this, projectName, buildNumber.toInteger(), tag)
}

def snykTest(String failOnIssues, String organisation, String severity, String targetFile = '', String pr) {
  Build.snykTest(this, failOnIssues, organisation, severity, targetFile, pr)
}
