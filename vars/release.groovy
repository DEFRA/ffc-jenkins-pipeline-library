import uk.gov.defra.ffc.Release

def trigger(String versionTag, String repoName, String releaseDescription, String token) {
  return Release.trigger(this, versionTag, repoName, releaseDescription, token)
}

def addSemverTags(String version, String repoName) {
  Release.addSemverTags(this, version, repoName)
}
