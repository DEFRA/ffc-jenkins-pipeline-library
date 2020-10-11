library("defra-library@$BRANCH_NAME")

def pr = ''
def repoName = ''
def versionFileName = 'VERSION'

node {
  try {
    stage('Checkout source code') {
      build.checkoutSourceCode()
    }

    stage('Set PR and version variables') {
      (repoName, pr, versionTag) = build.getVariables(version.getFileVersion(versionFileName))
    }

    // not needed when semantic-release is running
    if (pr != '') {
      stage('Verify version incremented') {
        version.verifyFileIncremented(versionFileName)
      }
    }

    stage('Run commitlint') {
      sh('./scripts/commitlint-branch')
    }

    stage('Run GitHub Super-Linter') {
      echo('Skipping step to save time during testing.')
      /* test.runGitHubSuperLinter() */
    }

    // This takes the place of the GitHub release below
    stage('Run semantic-release') {
      withCredentials([
        string(credentialsId: 'github-auth-token', variable: 'GH_TOKEN')
      ]) {
        sh("GH_TOKEN=$GH_TOKEN ./scripts/semantic-release")
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
    notifySlack.buildFailure('#generalbuildfailures')
    throw e
  }
}
