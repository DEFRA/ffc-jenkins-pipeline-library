library("defra-library@$BRANCH_NAME")

def pr = ''
def repoName = ''
def versionFileName = 'VERSION'
def postTestTasks = {
def version = version.getPackageJsonVersion()
def commitSha = utils.getCommitSha()

node {
  checkout scm

  try {
    stage('Set PR and version variables') {
      (repoName, pr, versionTag) = build.getVariables(version.getFileVersion(versionFileName))
    }
    if (pr != '') {
      stage('Verify version incremented') {
        version.verifyFileIncremented(versionFileName)
      }
    } else {
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
    
  echo "repo name is $repoName"
  stage('Publish Pact to broker') {
    withCredentials([
      string(credentialsId: 'pact-broker-url', variable: 'pactBrokerURL'),
      usernamePassword(credentialsId: 'pact-broker-credentials', usernameVariable: 'pactUsername', passwordVariable: 'pactPassword')
    ]) {
      dir('test-output') {
        echo "Publish pacts to broker"
        def pacts = findFiles glob: "*.json"
        echo "Found ${pacts.size()} pact file(s) to publish"
        for (pact in pacts) {
          def provider = pact.name.substring("$repoName-".length(), pact.name.indexOf(".json"))
          echo "Publishing ${pact.name} to broker"
          sh "curl -k -v -XPUT -H \"Content-Type: application/json\" --user $pactUsername:$pactPassword -d@${pact.name} $pactBrokerURL/pacts/provider/$provider/consumer/$repoName/version/$version+$commitSha"
        }
      }
    }
  }
  

  } catch(e) {
    notifySlack.buildFailure(e.message, '#generalbuildfailures')
    throw e
  }
}
