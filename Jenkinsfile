File sourceFile = new File("src/uk/gov/defra/ffc/DefraUtils.groovy");
Class groovyClass = new GroovyClassLoader(getClass().getClassLoader()).parseClass(sourceFile);
GroovyObject defraUtils = (GroovyObject) groovyClass.newInstance();

def mergedPrNo = ''
def pr = ''
def version = ''
def serviceName = 'ffc-jenkins-pipeline-library'

node {
  checkout scm

  try {
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
