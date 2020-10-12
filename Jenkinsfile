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
      // This function call should not be required when semantic-release is
      // running as semantic-release will only when on a release branch so no
      // need to check for pr. repoName and versionTag are only used in the
      // release which will be done by semantic-release
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
      // turn this back on when testing is complete
      // test.runGitHubSuperLinter()
    }

    stage('Get version') {
      withCredentials([
        string(credentialsId: 'github-auth-token', variable: 'GH_TOKEN')
      ]) {
        def nextVersion = sh(returnStdout: true, script: "GH_TOKEN=$GH_TOKEN ./scripts/semantic-release | awk '/The next release version is / {print \$12}'").trim()
        echo("Next version: '$nextVersion'")
      }
    }

    // This takes the place of the GitHub release below. Here for testing.
    // No need to check for 'pr' as it is done automatically
    stage('Run semantic-release') {
      withCredentials([
        string(credentialsId: 'github-auth-token', variable: 'GH_TOKEN')
      ]) {
        // will need to pass 'release' to script for a release to happen
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
