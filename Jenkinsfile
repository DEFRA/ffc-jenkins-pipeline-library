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

    stage('Run commitlint') {
      sh('./scripts/commitlint-container')
    }

    stage('Run GitHub Super-Linter') {
      echo('Skipping step to save time during testing.')
      /* test.runGitHubSuperLinter() */
    }

    stage('Run semantic-release') {
      withCredentials([
        string(credentialsId: 'github-auth-token', variable: 'gitToken')
      ]) {
        sh("GH_TOKEN=$gitToken ./scripts/semantic-release")
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
    notifySlack.buildFailure(e.message, '#generalbuildfailures')
    throw e
  }
}
