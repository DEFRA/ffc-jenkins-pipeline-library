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
