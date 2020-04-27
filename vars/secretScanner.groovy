def scanWithinWindow(githubOrg, repositoryPrefix, scanWindowHrs) {
  withCredentials([string(credentialsId: 'github-auth-token', variable: 'githubToken')]) {
    def curlAuth = "curl --header 'Authorization: token $githubToken' --silent"
    def githubApiUrl = "https://api.github.com/orgs/$githubOrg/repos?per_page=100"

    def curlHeaders = sh(returnStdout: true, script: "$curlAuth --head $githubApiUrl")
    // def curlHeaders = getHeadersCurlCmd.execute().text.split('\n')
    def numPages = "1"

    echo "$curlHeaders"

    curlHeaders.each {
      if (it.startsWith('Link:')) {
        numPages = it[(it.lastIndexOf('&page=')+6)..(it.lastIndexOf('>')-1)]
      }
    }

    echo "$numPages"
  }
}