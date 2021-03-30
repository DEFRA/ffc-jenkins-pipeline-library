import uk.gov.defra.ffc.ConsoleLogs

void save(String jenkinsUrl, String repoName, String branch, String buildNumber) {
  ConsoleLogs.save(this, jenkinsUrl, repoName, branch, buildNumber)
}

void save(String jenkinsUrl, String repoName, String buildNumber) {
  ConsoleLogs.save(this, jenkinsUrl, repoName, buildNumber)
}