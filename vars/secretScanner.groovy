// private
@NonCPS // Don't run this in the Jenkins sandbox so that use (groovy.time.TimeCategory) will work
def getCommitCheckDate(scanWindowHrs) {
  use (groovy.time.TimeCategory) {
    def commitCheckDate = (new Date() - scanWindowHrs.hours).format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))
    return commitCheckDate
  }
}

// public
def scanWithinWindow(dockerImgName, githubOwner, repositoryPrefix, scanWindowHrs) {
  withCredentials([string(credentialsId: 'github-auth-token', variable: 'githubToken')]) {
    def curlAuth = "curl --header 'Authorization: token $githubToken' --silent"
    def githubReposUrl = "https://api.github.com/users/$githubOwner/repos?per_page=100"

    def curlHeaders = sh returnStdout: true, script: "$curlAuth --head $githubReposUrl"
    def numPages = "1"

    curlHeaders.split('\n').each { header ->
      if (header.startsWith('Link:')) {
        numPages = header[(header.lastIndexOf('&page=')+6)..(header.lastIndexOf('>')-1)]
      }
    }

    echo "Number of pages of repos: $numPages"

    def matchingRepos = []
    def matchStr = "$githubOwner/$repositoryPrefix".toLowerCase()

    (numPages as Integer).times { page ->
      def reposResults = sh returnStdout: true, script: "$curlAuth $githubReposUrl\\&page=${page+1}"
      def allRepos = readJSON text: reposResults

      allRepos.each { repo ->
        if (repo.full_name.toLowerCase().startsWith(matchStr)) {
          matchingRepos.add(repo.full_name)
        }
      }
    }

    echo "Matching repos: $matchingRepos"

    def commitCheckDate = getCommitCheckDate(scanWindowHrs)
    def secretsFound = false

    echo "Commit check date: $commitCheckDate"

    matchingRepos.each { repo ->
      echo "Scanning $repo"

      def githubBranchUrl = "https://api.github.com/repos/$repo/branches"
      def branchResults = sh returnStdout: true, script: "$curlAuth $githubBranchUrl"
      def branches = readJSON text: branchResults
      def commitShas = []

      branches.each { branch ->
        def githubCommitUrl = "https://api.github.com/repos/$repo/commits?since=$commitCheckDate\\&sha=${branch.name}"
        def commitResults = sh returnStdout: true, script: "$curlAuth $githubCommitUrl"
        def commits = readJSON text: commitResults

        if (commits.size() > 0) {
          commits.each {
            commitShas.add(it.sha)
          }
        }
      }

      if (commitShas.size() > 0) {
        // truffleHog seems to alway exit with code 1 even though it appears to run fine
        // which fails the build so we need the || true to ignore the exit code and carry on
        def truffleHogCmd = "docker run $dockerImgName --json --regex https://github.com/${repo}.git || true"
        def truffleHogResults = sh returnStdout: true, script: truffleHogCmd
        def secretMessages = []

        truffleHogResults.trim().split('\n').each {
          if (it.length() == 0) return  // readJSON won't accept an empty string
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
          def msg = "POTENTIAL SECRETS DETECTED IN $repo\n${JOB_NAME}/${BUILD_NUMBER}\n(<${BUILD_URL}|Open>)"
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

// public
def scanFullHistory(dockerImgName, githubOwner, repositoryPrefix) {
  withCredentials([string(credentialsId: 'github-auth-token', variable: 'githubToken')]) {
    def curlAuth = "curl --header 'Authorization: token $githubToken' --silent"
    def githubReposUrl = "https://api.github.com/users/$githubOwner/repos?per_page=100"

    def curlHeaders = sh returnStdout: true, script: "$curlAuth --head $githubReposUrl"
    def numPages = "1"

    curlHeaders.split('\n').each { header ->
      if (header.startsWith('Link:')) {
        numPages = header[(header.lastIndexOf('&page=')+6)..(header.lastIndexOf('>')-1)]
      }
    }

    echo "Number of pages of repos: $numPages"

    def matchingRepos = []
    def matchStr = "$githubOwner/$repositoryPrefix".toLowerCase()

    (numPages as Integer).times { page ->
      def reposResults = sh returnStdout: true, script: "$curlAuth $githubReposUrl\\&page=${page+1}"
      def allRepos = readJSON text: reposResults

      allRepos.each { repo ->
        if (repo.full_name.toLowerCase().startsWith(matchStr)) {
          matchingRepos.add(repo.full_name)
        }
      }
    }

    echo "Matching repos: $matchingRepos"

    def secretsFound = false

    matchingRepos.each { repo ->
      echo "Scanning $repo"

      // truffleHog seems to alway exit with code 1 even though it appears to run fine
      // which fails the build so we need the || true to ignore the exit code and carry on
      def truffleHogCmd = "docker run $dockerImgName --json --regex https://github.com/${repo}.git || true"
      def truffleHogResults = sh returnStdout: true, script: truffleHogCmd
      def secretMessages = []

      truffleHogResults.trim().split('\n').each {
        if (it.length() == 0) return  // readJSON won't accept an empty string
        def result = readJSON text: it

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

      if (secretMessages.size() > 0) {
        def msg = "POTENTIAL SECRETS DETECTED IN $repo\n${JOB_NAME}/${BUILD_NUMBER}\n(<${BUILD_URL}|Open>)"
        def channel = "#secretdetection"

        slackSend channel: channel,
                  color: "#ff0000",
                  message: msg

        secretMessages.each {
          echo "$it"
        }
      }

      echo "Finished scanning $repo"
    }

    return secretsFound
  }
}