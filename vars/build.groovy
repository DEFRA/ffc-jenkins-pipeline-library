import uk.gov.defra.ffc.Build
import uk.gov.defra.ffc.Docker
import uk.gov.defra.ffc.Tests

def getVariables(version) {
  return Build.getVariables(this, version)
}

def setGithubStatusSuccess(message = 'Build successful') {
  Build.updateGithubCommitStatus(this, message, 'SUCCESS')
}

def setGithubStatusPending(message = 'Build started') {
  Build.updateGithubCommitStatus(this, message, 'PENDING')
}

def setGithubStatusFailure(message = '') {
  Build.updateGithubCommitStatus(this, message, 'FAILURE')
}

def buildTestImage(credentialsId, registry, projectName, buildNumber, tag) {
  Docker.buildTestImage(this, credentialsId, registry, projectName, buildNumber, tag)
}

def runTests(projectName, serviceName, buildNumber, tag) {
  Tests.runTests(this, projectName, serviceName, buildNumber, tag)
}

def buildAndPushContainerImage(credentialsId, registry, imageName, tag) {
  Docker.buildAndPushContainerImage(this, credentialsId, registry, imageName, tag)
}

def npmAudit(auditLevel, logType, failOnIssues, containerImage, containerWorkDir) {
  Build.npmAudit(this, auditLevel, logType, failOnIssues, containerImage, containerWorkDir)
}

def extractContainerObjFiles(projectName) {
  Build.extractContainerObjFiles(this, projectName)
}

def snykTest(failOnIssues, organisation, severity, targetFile = '') {
  Build.snykTest(this, failOnIssues, organisation, severity, targetFile)
}
