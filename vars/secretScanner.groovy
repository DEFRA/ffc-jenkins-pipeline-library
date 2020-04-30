// FIXME: set up exclude file with package-lock and build own docker container

// private
@NonCPS // Don't run this in the Jenkins sandbox to make groovy.time.TimeCategory work
def getCommitCheckDate(scanWindowHrs) {
  use (groovy.time.TimeCategory) {
    def commitCheckDate = (new Date() - scanWindowHrs.hours).format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))
    return commitCheckDate
  }
}

// public
def scanWithinWindow(githubOwner, repositoryPrefix, scanWindowHrs) {
  withCredentials([string(credentialsId: 'github-auth-token', variable: 'githubToken')]) {
    def curlAuth = "curl --header 'Authorization: token $githubToken' --silent"
    def githubApiUrl = "https://api.github.com/users/$githubOwner/repos?per_page=100"

    def curlHeaders = sh returnStdout: true, script: "$curlAuth --head $githubApiUrl"
    def numPages = "1"

    curlHeaders.split('\n').each {
      if (it.startsWith('Link:')) {
        numPages = it[(it.lastIndexOf('&page=')+6)..(it.lastIndexOf('>')-1)]
      }
    }

    echo "Number of pages of repos: $numPages"

    def matchingRepos = []
    def matchStr = "$githubOwner/$repositoryPrefix".toLowerCase()

    (numPages as Integer).times {
      def reposResult = sh returnStdout: true, script: "$curlAuth $githubApiUrl\\&page=${it+1}"
      def result = readJSON text: reposResult

      result.each {
        if (it.full_name.toLowerCase().startsWith(matchStr)) {
          matchingRepos.add(it.full_name)
        }
      }
    }

    echo "Matching repos: $matchingRepos"

    def commitCheckDate = getCommitCheckDate(scanWindowHrs)

    echo "Commit check date: $commitCheckDate"

    sh "docker pull dxa4481/trufflehog"

    def secretsFound = false

    matchingRepos.each { repo ->
      echo "Scanning $repo"

      def githubBranchUrl = "https://api.github.com/repos/$repo/branches"
      def branchResult = sh returnStdout: true, script: "$curlAuth $githubBranchUrl"
      def branches = readJSON text: branchResult
      def commitShas = []

      branches.each { branch ->
        def githubApiCommitUrl = "https://api.github.com/repos/$repo/commits?since=$commitCheckDate\\&sha=${branch.name}"
        def commitResult = sh returnStdout: true, script: "$curlAuth $githubApiCommitUrl"
        def commits = readJSON text: commitResult

        if (commits.size() > 0) {
          commits.each {
            commitShas.add(it.sha)
          }
        }
      }

      if (commitShas.size() > 0) {
        // The truffleHog docker run causes exit code 1 which fails the build so need the || true to ignore it
        def truffleHogCmd = "docker run dxa4481/trufflehog --json --regex https://github.com/${repo}.git || true"
        def truffleHogRes = sh returnStdout: true, script: truffleHogCmd
        def secretMessages = []

        truffleHogRes.trim().split('\n').each {
          def result = readJSON text: it

          if (commitShas.contains(result.commitHash)) {
            def message = "Reason: $result.reason\n" +
                          "Date: $result.date\n" +
                          "Branch: $result.branch\n" +
                          "Hash: $result.commitHash\n" +
                          "Filepath: $result.path\n" +
                          "StringsFound: $result.stringsFound\n" +
                          "Commit: $result.commit"

            secretMessages.add(message)
            secretsFound = true
          }
        }

        if (secretMessages.size() > 0) {
          def msg = "POTENTIAL SECRETS DETECTED\n${JOB_NAME}/${BUILD_NUMBER}\n(<${BUILD_URL}|Open>)"
          def channel = "#secretdetection"

          slackSend channel: channel,
                    color: "#ff0000",
                    message: msg

          secretMessages.each {
            echo "$it"
          }
        }
      }

      echo "Finished scanning $repo"
    }

    return secretsFound
  }
}