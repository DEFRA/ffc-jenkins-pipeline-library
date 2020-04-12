def mergedPrNo = ''
def pr = ''
def currentVersion = ''
def repoName = ''
def versionFileName = "VERSION"

def build
def version
def release
def notifySlack

node {
  checkout scm

  try {
    stage('Load library functions') {
      build = load 'build.groovy'
      version = load 'version.groovy'
      release = load 'release.groovy'
      notifySlack = load 'notifySlack.groovy'
    }
    stage('Set GitHub status as pending'){
      build.setGithubStatusPending()
    }
    stage('Set PR and version variables') {
      (repoName, pr, containerTag, mergedPrNo) = build.getVariables(version.getFileVersion(versionFileName))
      currentVersion = version.getFileVersion(versionFileName)
    }
    if (pr != '') {
      stage('Verify version incremented') {
        version.verifyFileVersionIncremented(versionFileName)
      }
    }
    else {
      stage('Trigger GitHub release') {
        withCredentials([
          string(credentialsId: 'github-auth-token', variable: 'gitToken')
        ]) {
          def releaseSuccess = release.trigger(currentVersion, serviceName, currentVersion, gitToken)

          if (releaseSuccess) {
            release.addSemverTags(version, serviceName)
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
