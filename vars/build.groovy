import uk.gov.defra.ffc.Build
import uk.gov.defra.ffc.Docker
import uk.gov.defra.ffc.Tests

def getVariables(version) {
  return Build.getVariables(this, version)
}

def checkoutSourceCode() {
  return Build.checkoutSourceCode(this)
}

def buildTestImage(credentialsId, registry, projectName, buildNumber, tag) {
  Docker.buildTestImage(this, credentialsId, registry, projectName, buildNumber, tag)
}

def runTests(projectName, serviceName, buildNumber, tag, pr) {
  Tests.runTests(this, projectName, serviceName, buildNumber, tag, pr)
}

def buildAndPushContainerImage(credentialsId, registry, imageName, tag) {
  Docker.buildAndPushContainerImage(this, credentialsId, registry, imageName, tag)
}

def npmAudit(auditLevel, logType, failOnIssues, containerImage, containerWorkDir) {
  Build.npmAudit(this, auditLevel, logType, failOnIssues, containerImage, containerWorkDir)
}

def extractSynkFiles(projectName, buildNumber, tag) {
  Build.extractSynkFiles(this, projectName, buildNumber, tag)
}

def snykTest(failOnIssues, organisation, severity, targetFile = '') {
  Build.snykTest(this, failOnIssues, organisation, severity, targetFile)
}
