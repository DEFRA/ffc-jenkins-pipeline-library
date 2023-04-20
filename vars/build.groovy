import uk.gov.defra.ffc.Build
import uk.gov.defra.ffc.Docker
import uk.gov.defra.ffc.Tests

String [] getVariables(String version, String defaultBranch) {
  return Build.getVariables(this, version, defaultBranch)
}

String getDefaultBranch(String defaultBranch, String requestedBranch) {
  return Build.getDefaultBranch(defaultBranch, requestedBranch)
}

String getNodeTestVersion(String defaultNodeTestVersion, String requestedNodeTestVersion) {
  return Build.getNodeTestVersion(defaultNodeTestVersion, requestedNodeTestVersion)
}

String checkoutSourceCode(String defaultBranch) {
  return Build.checkoutSourceCode(this, defaultBranch)
}

void buildTestImage(String credentialsId, String registry, String projectName, String buildNumber, String tag) {
  Docker.buildTestImage(this, credentialsId, registry, projectName, buildNumber, tag)
}

void runNodeTestImage(nodeTestImage, repoName) {
  Docker.runNodeTestImage(this, nodeTestImage, repoName)
}

void runTests(String projectName, String serviceName, String buildNumber, String tag, String pr, String environment) {
  Tests.runTests(this, projectName, serviceName, buildNumber, tag, pr, environment)
}

void runServiceAcceptanceTests(String projectName, String serviceName, String buildNumber, String tag, String pr) {
  Tests.runServiceAcceptanceTests(this, projectName, serviceName, buildNumber, tag, pr)
}

void buildAndPushContainerImage(String credentialsId, String registry, String imageName, String tag) {
  Docker.buildAndPushContainerImage(this, credentialsId, registry, imageName, tag)
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

String getImageName(String repoName, String tag, String tagSuffix = null, String registry = null) {
  Docker.getImageName(repoName, tag, tagSuffix, registry)
}

void buildContainerImage(String imageName) {
  Docker.buildContainerImage(this, imageName)
}

void pushContainerImage(String imageName) {
  Docker.pushContainerImage(this, imageName)
}

Boolean containerTagExists(String imageName) {
  return Docker.containerTagExists(this, imageName)
}

void triggerMultiBranchBuilds(String defaultBranch) {
  Build.triggerMultiBranchBuilds(this, defaultBranch)
}
