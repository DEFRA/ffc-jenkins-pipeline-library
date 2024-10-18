import uk.gov.defra.ffc.Release

Boolean trigger(String versionTag, String repoName, String releaseDescription, String token, prerelease=false) {
  return Release.trigger(this, versionTag, repoName, releaseDescription, token, prerelease)
}

void addSemverTags(String version, String repoName) {
  Release.addSemverTags(this, version, repoName)
}
