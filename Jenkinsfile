@Library('defra-library@psd-593-add-library-build-functions')
import uk.gov.defra.ffc.DefraUtils
def defraUtils = new DefraUtils()

def mergedPrNo = ''
def pr = ''
def version = ''
def serviceName = 'ffc-jenkins-pipeline-library'

node {
  checkout scm

  try {
    stage('Test load') {
      code = load 'src/uk/gov/defra/ffc/DefraUtils.groovy'
      code.tagCommit("test", "test2")
    }
    stage('Set GitHub status as pending'){
      defraUtils.setGithubStatusPending()
    }
    stage('Set branch, PR, and containerTag variables') {
      (pr, version, mergedPrNo) = defraUtils.getVariables(serviceName, defraUtils.getFileVersion())
    }
    if (pr != '') {
      stage('Verify version incremented') {
        defraUtils.verifyFileVersionIncremented()
      }
    }
    else {
      stage('Trigger GitHub release') {
        withCredentials([
          string(credentialsId: 'github-auth-token', variable: 'gitToken')
        ]) {
          defraUtils.triggerRelease(containerTag, serviceName, containerTag, gitToken)
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
