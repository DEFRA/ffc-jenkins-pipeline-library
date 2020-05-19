import uk.gov.defra.ffc.Release
import uk.gov.defra.ffc.Utils

def trigger(versionTag, repoName, releaseDescription, token){
  if (Release.exists(this, versionTag, repoName, token)){
    echo("Release $versionTag already exists")
    return false
  }

  echo("Triggering release $versionTag for $repoName")
  boolean result = false
  result = sh(returnStdout: true, script: "curl -s -X POST -H 'Authorization: token $token' -d '{ \"tag_name\" : \"$versionTag\", \"name\" : \"Release $versionTag\", \"body\" : \" Release $releaseDescription\" }' https://api.github.com/repos/DEFRA/$repoName/releases")
  echo("The release result is $result")

  if (Release.exists(this, versionTag, repoName, token)){
    echo('Release Successful')
  } else {
    throw new Exception('Release failed')
  }

  return true
}

def addSemverTags(version, repoName) {
  def versionList = version.tokenize('.')
  assert versionList.size() == 3

  def majorTag = "${versionList[0]}"
  def minorTag = "${versionList[0]}.${versionList[1]}"
  def commitSha = Utils.getCommitSha()

  Release.tagCommit(this, minorTag, commitSha, repoName)
  Release.tagCommit(this, majorTag, commitSha, repoName)
}
