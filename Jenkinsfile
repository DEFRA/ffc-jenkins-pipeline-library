def defraUtils

def mergedPrNo = ''
def pr = ''
def version = ''
def serviceName = 'ffc-jenkins-pipeline-library'
def versionFileName = "VERSION"

node {
  checkout scm

  try {
    stage('Load defraUtils functions') {
      defraUtils = load 'src/uk/gov/defra/ffc/DefraUtils.groovy'
    }
    stage('Set GitHub status as pending'){
      defraUtils.setGithubStatusPending()
    }
    stage('Set PR and version variables') {
      (pr, containerTag, mergedPrNo) = defraUtils.getVariables(serviceName, defraUtils.getFileVersion(versionFileName))
      version = defraUtils.getFileVersion(versionFileName)
    }
    if (pr != '') {
      stage('Verify version incremented') {
        defraUtils.verifyFileVersionIncremented(versionFileName)
        // FIXME: this following line is here for testing only
        defraUtils.tagCommit(version, gitToken)
      }
    }
    else {
      stage('Trigger GitHub release') {
        withCredentials([
          string(credentialsId: 'github-auth-token', variable: 'gitToken')
        ]) {
          defraUtils.triggerRelease(version, serviceName, version, gitToken)
          // defraUtils.tagCommit(version)
        }
      }
    }
    stage('Set GitHub status as success'){
      defraUtils.setGithubStatusSuccess()
    }
  } catch(e) {
    defraUtils.setGithubStatusFailure(e.message)
    defraUtils.notifySlackBuildFailure(e.message, "#generalbuildfailures")
    throw e
  }
}
