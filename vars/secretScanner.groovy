def scanWithinWindow(githubOrg, repositoryPrefix, scanWindowHrs) {
  withCredentials([string(credentialsId: 'github-auth-token', variable: 'githubToken')]) {
    def curlAuth = "curl --header 'Authorization: token $githubToken' --silent"
    def githubApiUrl = "https://api.github.com/orgs/$githubOrg/repos?per_page=100"

    def curlHeaders = sh(returnStdout: true, script: "$curlAuth --head $githubApiUrl")
    def numPages = "1"

    curlHeaders.split('\n').each {
      if (it.startsWith('Link:')) {
        numPages = it[(it.lastIndexOf('&page=')+6)..(it.lastIndexOf('>')-1)]
      }
    }

    echo "Number of pages of repos: $numPages"

    def matchingRepos = []

    (numPages as Integer).times {
      def reposCmd = "$curlAuth $githubApiUrl\\&page=${it+1} | jq '.[] | .full_name'"
      echo "$reposCmd"

      // FIXME: look into reading this into groovy JSON object instead of using jq
      def reposResult = sh(returnStdout: true, script: reposCmd).trim().replaceAll (/"/, '')
      echo "reposResult: $reposResult"
      // def reposList = []

      // if (reposResult.length() > 0) {
      //   reposList = reposResult.split('\n').collect {
      //     return it[1..-2]
      //   }
      // }

      // matchingRepos += reposList
    }

    echo "Matching repos: $matchingRepos"

  }
}