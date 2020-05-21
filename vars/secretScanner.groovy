import uk.gov.defra.ffc.SecretScanner

def scanWithinWindow(githubCredentialId, dockerImgName, githubOwner, repositoryPrefix, scanWindowHrs, excludeStrings, slackChannel="") {
  return SecretScanner.scanWithinWindow(this, githubCredentialId, dockerImgName, githubOwner, repositoryPrefix, scanWindowHrs, excludeStrings, slackChannel)
}

def scanFullHistory(githubCredentialId, dockerImgName, githubOwner, repositoryPrefix, excludeStrings, slackChannel="") {
  return SecretScanner.scanFullHistory(this, githubCredentialId, dockerImgName, githubOwner, repositoryPrefix, excludeStrings, slackChannel)
}
