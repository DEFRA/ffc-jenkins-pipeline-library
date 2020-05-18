def cleanupKubernetes(environment) {
  if (repoName == '' || SOURCE_PROJECT_NAME == '') {
    echo "Unable to determine repo name and branch name, k8s cleanup cancelled"
  } else {
    def apiParams = "state=closed&sort=updated&direction=desc&head=DEFRA:$SOURCE_PROJECT_NAME"
    def apiUrl = "https://api.github.com/repos/DEFRA/$repoName/pulls?$apiParams"
    def closedPrNo = sh(returnStdout: true, script: "curl '$apiUrl' | jq '.[].number'").trim()
    if (closedPrNo == '') {
      echo "Could not find closed PR for branch $SOURCE_PROJECT_NAME of $repoName, k8s cleanup cancelled"
    }else {
        echo "Tidying up k8s resources for PR $closedPrNo of $repoName after branch $SOURCE_PROJECT_NAME deleted"
        helm.undeployChart(environment, repoName, "pr$closedPrNo")
    }
  }
}
