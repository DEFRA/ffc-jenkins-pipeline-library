import uk.gov.defra.ffc.Build
import uk.gov.defra.ffc.Docker
import uk.gov.defra.ffc.Tests

def getVariables(version, defaultBranch) {
  return Build.getVariables(this, version, defaultBranch)
}

def getDefaultBranch(defaultBranch, requestedBranch) {
  return Build.getDefaultBranch(defaultBranch, requestedBranch)
}

def checkoutSourceCode(defaultBranch) {
  return Build.checkoutSourceCode(this, defaultBranch)
}

def buildTestImage(credentialsId, registry, projectName, buildNumber, tag) {
  Docker.buildTestImage(this, credentialsId, registry, projectName, buildNumber, tag)
}

def runTests(projectName, serviceName, buildNumber, tag, pr, environment) {
  Tests.runTests(this, projectName, serviceName, buildNumber, tag, pr, environment)
}

def buildAndPushContainerImage(credentialsId, registry, imageName, tag) {
  Docker.buildAndPushContainerImage(this, credentialsId, registry, imageName, tag)
}

def npmAudit(auditLevel, logType, failOnIssues, containerImage, containerWorkDir, pr) {
  Build.npmAudit(this, auditLevel, logType, failOnIssues, containerImage, containerWorkDir, pr)
}

def extractSynkFiles(projectName, buildNumber, tag) {
  Build.extractSynkFiles(this, projectName, buildNumber, tag)
}

def snykTest(failOnIssues, organisation, severity, targetFile = '', pr) {
  Build.snykTest(this, failOnIssues, organisation, severity, targetFile, pr)
}
