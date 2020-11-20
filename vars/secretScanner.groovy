import uk.gov.defra.ffc.SecretScanner

Boolean scanWithinWindow(String githubCredentialId, String dockerImgName, String githubOwner, String repositoryPrefix, Date() scanWindowHrs, String[] excludeStrings, String slackChannel="") {
  return SecretScanner.scanWithinWindow(this, githubCredentialId, dockerImgName, githubOwner, repositoryPrefix, scanWindowHrs, excludeStrings, slackChannel)
}

Boolean scanFullHistory(String githubCredentialId, String dockerImgName, String githubOwner, String repositoryPrefix, String[] excludeStrings, String slackChannel="") {
  return SecretScanner.scanFullHistory(this, githubCredentialId, dockerImgName, githubOwner, repositoryPrefix, excludeStrings, slackChannel)
}
