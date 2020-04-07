// private
def releaseExists(containerTag, repoName, token){
  try {
    def result = sh(returnStdout: true, script: "curl -s -H 'Authorization: token $token' https://api.github.com/repos/DEFRA/$repoName/releases/tags/$containerTag | jq '.tag_name'").trim().replaceAll (/"/, '') == "$containerTag" ? true : false
    return result
  }
    catch(Exception ex) {
    echo "Failed to check release status on github"
    throw new Exception (ex)
  }
}

// public
def trigger(containerTag, releaseDescription, token){
  def repoName = build.getRepoName()
  if (releaseExists(containerTag, repoName, token)){
    echo "Release $containerTag already exists"
    return false
  }

  echo "Triggering release $containerTag for $repoName"
  boolean result = false
  result = sh(returnStdout: true, script: "curl -s -X POST -H 'Authorization: token $token' -d '{ \"tag_name\" : \"$containerTag\", \"name\" : \"Release $containerTag\", \"body\" : \" Release $releaseDescription\" }' https://api.github.com/repos/DEFRA/$repoName/releases")
  echo "The release result is $result"

  if (releaseExists(containerTag, repoName, token)){
    echo "Release Successful"
  } else {
    throw new Exception("Release failed")
  }

  return true
}

// private
def tagCommit(tag, commitSha, repoName) {
  dir('attachTag') {
    sshagent(['ffc-jenkins-pipeline-library-deploy-key']) {
      git credentialsId: 'ffc-jenkins-pipeline-library-deploy-key', url: "git@github.com:DEFRA/${repoName}.git"
      sh("git push origin :refs/tags/$tag")
      sh("git tag -f $tag $commitSha")
      sh("git push origin $tag")
    }
    deleteDir()
  }
}

// public
def addSemverTags(version, repoName) {
  def versionList = version.tokenize('.')
  assert versionList.size() == 3

  def majorTag = "${versionList[0]}"
  def minorTag = "${versionList[0]}.${versionList[1]}"
  def commitSha = getCommitSha()

  tagCommit(minorTag, commitSha, repoName)
  tagCommit(majorTag, commitSha, repoName)
}
