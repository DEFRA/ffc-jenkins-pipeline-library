import uk.gov.defra.ffc.Release

def trigger(versionTag, repoName, releaseDescription, token) {
  return Release.trigger(this, versionTag, repoName, releaseDescription, token)
}

def addSemverTags(version, repoName) {
  Release.addSemverTags(this, version, repoName)
}
