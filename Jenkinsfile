@Library('defra-library@fix-missing-method') _

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
      (repoName, pr, versionTag) = build.getVariables(version.getFileVersion(versionFileName))
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
          def releaseSuccess = release.trigger(versionTag, repoName, versionTag, gitToken)

          if (releaseSuccess) {
            release.addSemverTags(versionTag, repoName)
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
