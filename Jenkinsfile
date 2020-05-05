@Library('defra-library@psd-732-use-global-vars') _

def libraryVersion = ''
def mergedPrNo = ''
def pr = ''
def repoName = ''
def versionFileName = "VERSION"

node {
  checkout scm

  try {
    stage('Set GitHub status as pending'){
      build.setGithubStatusPending()
    }
    stage('Set PR and version variables') {
      libraryVersion = version.getFileVersion(versionFileName)
      (repoName, pr, containerTag, mergedPrNo) = build.getVariables(libraryVersion)
    }
    if (pr != '') {
      stage('Verify version incremented') {
        version.verifyFileIncremented(versionFileName)
      }
    }
    else {
      stage('Trigger GitHub release') {
        withCredentials([
          string(credentialsId: 'github-auth-token', variable: 'gitToken')
        ]) {
          def releaseSuccess = release.trigger(libraryVersion, repoName, libraryVersion, gitToken)

          if (releaseSuccess) {
            release.addSemverTags(libraryVersion, repoName)
          }
        }
      }
    }
    stage('Set GitHub status as success'){
      build.setGithubStatusSuccess()
    }
  } catch(e) {
    build.setGithubStatusFailure(e.message)
    notifySlack.buildFailure(e.message, "#generalbuildfailures")
    throw e
  }
}
