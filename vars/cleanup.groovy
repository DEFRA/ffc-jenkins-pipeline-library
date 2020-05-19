def call(Map config=[:]) {
  node {
    stage('delete resources of closed PR') {
      cleanup(config.environment, repoName, SOURCE_PROJECT_NAME)
    }
  }
}

def cleanup(environment, repoName, branchName) {
  if (repoName == '' || branchName == '') {
    echo "Unable to determine repo name and branch name, cleanup cancelled"
  } else {
    def apiParams = "state=closed&sort=updated&direction=desc&head=DEFRA:$branchName"
    def apiUrl = "https://api.github.com/repos/DEFRA/$repoName/pulls?$apiParams"
    def closedPrNo = sh(returnStdout: true, script: "curl '$apiUrl' | jq 'first | .number'").trim()
    if (closedPrNo == '' || closedPrNo == 'null') {
      echo "Could not find closed PR for branch $branchName of $repoName, cleanup cancelled"
    } else {
      echo "Tidying up kubernetes resources for PR $closedPrNo of $repoName after branch $branchName deleted"
      helm.undeployChart(environment, repoName, "pr$closedPrNo")
    }
  }
}
