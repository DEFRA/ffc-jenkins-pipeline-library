import groovy.json.JsonSlurper

// FIXME: set up exclude file with package-lock and build own docker container

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
    def jsonSlurper = new JsonSlurper()

    (numPages as Integer).times {
      // def reposCmd = "$curlAuth $githubApiUrl\\&page=${it+1} | jq '.[] | .full_name'"
      // FIXME: look into reading this into JSON slurper object instead of using jq
      def reposResult = sh returnStdout: true, script: "$curlAuth $githubApiUrl\\&page=${it+1}"

      reposResult.trim().split('\n').each {
        def result = jsonSlurper.parseText(it)

        print "$result.full_name"
        // FIXME: should use startsWith after '/'
        // if (it.contains(repositoryPrefix)) {
        //   matchingRepos.add(it)
        // }
      }
    }

    echo "Matching repos: $matchingRepos"

    def commitCheckDate = getCommitCheckDate(scanWindowHrs)

    echo "Commit check date: $commitCheckDate"

    sh "docker pull dxa4481/trufflehog"

    matchingRepos.each {
      echo "Scanning $it"

      // FIXME: use github API to check for only repos with a recent commit and only run truffleHog on these
      // Keep a record of the commit SHAs

      // The truffleHog docker run cause exit code 1 which fails the build so need the || true to ignore it
      def truffleHogCmd = "docker run dxa4481/trufflehog --json --regex https://github.com/${it}.git || true"
      def truffleHogRes = sh(returnStdout: true, script: truffleHogCmd).trim()
      def reportRes = []

      def secretsFound = false

      truffleHogRes.split('\n').each {
        def result = jsonSlurper.parseText(it)
        def dateObj = new Date().parse("yyyy-MM-dd HH:mm:ss", result.date)

        // FIXME: match by commit SHA instead of date when we use the github API to get only recent commits
        if (dateObj > commitCheckDate) {
          def message = "Reason: $result.reason\n" +
                        "Date: $result.date\n" +
                        "Branch: $result.branch\n" +
                        "Hash: $result.commitHash\n" +
                        "Filepath: $result.path\n" +
                        "StringsFound: $result.stringsFound\n" +
                        "Commit: $result.commit"
          print message
          secretsFound = true
        }
      }

      try {
        if (secretsFound) {
          def msg = """POTENTIAL SECRETS DETECTED
          ${JOB_NAME}/${BUILD_NUMBER}
          (<${BUILD_URL}|Open>)"""
          def channel = "#secretdetection"

          slackSend channel: channel,
                    color: "#ff0000",
                    message: msg.replace("  ", "")
        }
      } catch (e) { }

      echo "Finished scanning $it"
    }
  }
}