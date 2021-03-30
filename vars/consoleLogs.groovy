import uk.gov.defra.ffc.ConsoleLogs

void save(String jenkinsUrl, String repoName, String branch, String buildNumber, String logFilePath) {
  ConsoleLogs.save(this, jenkinsUrl, repoName, branch, buildNumber, logFilePath)
}

void save(String jenkinsUrl, String repoName, String buildNumber, String logFilePath) {
  ConsoleLogs.save(this, jenkinsUrl, repoName, buildNumber, logFilePath)
}