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

def npmAudit(auditLevel, logType, failOnIssues) {
  Build.npmAudit(this, auditLevel, logType, failOnIssues)
}

def snykTest(failOnIssues, organisation, severity) {
  failOnIssues = failOnIssues ?: false
  organisation = organisation ?: 'defra-4kb'
  severity = severity ?: 'medium'
  Build.snykTest(this, failOnIssues, organisation, severity)
}
