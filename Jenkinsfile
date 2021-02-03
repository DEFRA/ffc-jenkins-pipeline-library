library("defra-library@$BRANCH_NAME")

def defaultBranch = 'master'
def pr = ''
def repoName = ''
def versionFileName = 'VERSION'
String defaultBranch = 'master'

node {
  checkout scm

  try {
    stage('Set PR and version variables') {
      (repoName, pr, versionTag) = build.getVariables(version.getFileVersion(versionFileName), defaultBranch)
    }

    if (pr != '') {
      stage('Verify version incremented') {
        version.verifyFileIncremented(versionFileName)
      }
    }

    if (pr == '') {
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
  } catch(e) {
    echo("Build failed with message: $e.message")

    stage('Send build failure slack notification') {
      notifySlack.buildFailure('#generalbuildfailures', defaultBranch)
    }
    throw e
  }
}
