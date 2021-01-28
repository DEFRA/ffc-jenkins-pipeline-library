import uk.gov.defra.ffc.Build
import uk.gov.defra.ffc.Docker
import uk.gov.defra.ffc.Tests

String [] getVariables(String version, String defaultBranch) {
  return Build.getVariables(this, version, defaultBranch)
}

String getDefaultBranch(String defaultBranch, String requestedBranch) {
  return Build.getDefaultBranch(defaultBranch, requestedBranch)
}

String checkoutSourceCode(String defaultBranch) {
  return Build.checkoutSourceCode(this, defaultBranch)
}

void buildTestImage(String credentialsId, String registry, String projectName, String buildNumber, String tag) {
  Docker.buildTestImage(this, credentialsId, registry, projectName, buildNumber, tag)
}

void runTests(String projectName, String serviceName, String buildNumber, String tag, String pr, String environment) {
  Tests.runTests(this, projectName, serviceName, buildNumber, tag, pr, environment)
}

void buildAndPushContainerImage(String credentialsId, String registry, String imageName, String tag) {
  Docker.buildAndPushContainerImage(this, credentialsId, registry, imageName, tag)
}

void buildAndPushContainerImageCore(String credentialsId, String registry, String imageName, String tag) {
  Docker.buildAndPushContainerImageCore(this, credentialsId, registry, imageName, tag)
}

void npmAudit(String auditLevel, String logType, Boolean failOnIssues, String containerImage, String containerWorkDir, String pr) {
  Build.npmAudit(this, auditLevel, logType, failOnIssues, containerImage, containerWorkDir, pr)
}

void extractSynkFiles(String projectName, String buildNumber, String tag) {
  Build.extractSynkFiles(this, projectName, buildNumber, tag)
}

void snykTest(Boolean failOnIssues, String organisation, String severity, String targetFile = '', String pr) {
  Build.snykTest(this, failOnIssues, organisation, severity, targetFile, pr)
}
