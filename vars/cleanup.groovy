def call(Map config=[:]) {
  node {
    stage('delete resources of closed PR') {
      cleanup(config.environment)
    }
  }
}

def cleanup(environment) {
  if (repoName == '' || SOURCE_PROJECT_NAME == '') {
    echo "Unable to determine repo name and branch name, cleanup cancelled"
  } else {
    def apiParams = "state=closed&sort=updated&direction=desc&head=DEFRA:$SOURCE_PROJECT_NAME"
    def apiUrl = "https://api.github.com/repos/DEFRA/$repoName/pulls?$apiParams"
    def closedPrNo = sh(returnStdout: true, script: "curl '$apiUrl' | jq 'first | .number'").trim()
    if (closedPrNo == '' ||closedPrNo == 'null') {
      echo "Could not find closed PR for branch $SOURCE_PROJECT_NAME of $repoName, cleanup cancelled"
    }else {
      echo "Tidying up kubernetes resources for PR $closedPrNo of $repoName after branch $SOURCE_PROJECT_NAME deleted"
      helm.undeployChart(environment, repoName, "pr$closedPrNo")
    }
  }
}
