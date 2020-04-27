import groovy.json.JsonSlurper

@NonCPS // Don't run this in the Jenkins sandbox to make groovy.time.TimeCategory work
def getCommitCheckDate(scanWindowHrs) {
  use (groovy.time.TimeCategory) {
    def commitCheckDate = new Date() - scanWindowHrs.hours
    return commitCheckDate
  }
}

def scanWithinWindow(githubOrg, repositoryPrefix, scanWindowHrs) {
  withCredentials([string(credentialsId: 'github-auth-token', variable: 'githubToken')]) {
    def curlAuth = "curl --header 'Authorization: token $githubToken' --silent"
    // def githubApiUrl = "https://api.github.com/orgs/$githubOrg/repos?per_page=100"
    def githubApiUrl = "https://api.github.com/users/$githubOrg/repos?per_page=100"

    def curlHeaders = sh returnStdout: true, script: "$curlAuth --head $githubApiUrl"
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
      // FIXME: look into reading this into groovy JSON object instead of using jq
      def reposResult = sh(returnStdout: true, script: reposCmd).trim().replaceAll (/"/, '')

      reposResult.split('\n').each {
        if (it.contains(repositoryPrefix)) {
          matchingRepos.add(it)
        }
      }
    }

    echo "Matching repos: $matchingRepos"

    def commitCheckDate = getCommitCheckDate(scanWindowHrs)

    echo "Commit check date: $commitCheckDate"

    sh "docker pull dxa4481/trufflehog"

    try {
      matchingRepos.each {
        def truffleHogCmd = "docker run dxa4481/trufflehog --json --regex https://github.com/${it}.git"
        def truffleHogRes = sh(returnStdout: true, script: truffleHogCmd).trim()

        echo "HERE 1"

        // def reportRes = []
        // def jsonSlurper = new JsonSlurper()

        // echo "HERE 2"

        // truffleHogRes.split('\n').each {
        //   echo "HERE 3"
        //   def result = jsonSlurper.parseText(it)
        //   def dateObj = new Date().parse("yyyy-MM-dd HH:mm:ss", result.date)

        //   if (dateObj > commitCheckDate) {
        //     def message = "Reason: $result.reason\n" +
        //                   "Date: $result.date\n" +
        //                   "Hash: $result.commitHash\n" +
        //                   "Filepath: $result.path\n" +
        //                   "Branch: $result.branch\n" +
        //                   "Commit: $result.commit\n"
        //     print message
        //   }
        }
      }
    } catch (e) {
      echo "Kaboom"
      echo "$e"
    }
  }
}