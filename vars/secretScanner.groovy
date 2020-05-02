// private
@NonCPS // Don't run this in the Jenkins sandbox so that use (groovy.time.TimeCategory) will work
def getCommitCheckDate(scanWindowHrs) {
  use (groovy.time.TimeCategory) {
    return (new Date() - scanWindowHrs.hours - 10.minutes).format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))
  }
}

// private
def runTruffleHog(dockerImgName, repoName, commitShas=null) {
  // truffleHog seems to alway exit with code 1 even though it appears to run fine
  // which fails the build so we need the || true to ignore the exit code and carry on
  def truffleHogCmd = "docker run $dockerImgName --json https://github.com/${repoName}.git || true"
  def truffleHogResults = sh returnStdout: true, script: truffleHogCmd
  def secretMessages = []

  truffleHogResults.trim().split('\n').each {
    if (it.length() == 0) return  // readJSON won't accept an empty string
    def result = readJSON text: it

    if (!commitShas || commitShas.contains(result.commitHash)) {
      def message = "Reason: $result.reason\n" +
                    "Date: $result.date\n" +
                    "Repo: $repoName\n" +
                    "Branch: $result.branch\n" +
                    "Hash: $result.commitHash\n" +
                    "File path: $result.path\n" +
                    "Strings found: $result.stringsFound\n"

      secretMessages.add(message)
    }
  }

  return secretMessages
}

// private
def getNumPages(curlHeaders) {
  def linkHeader = curlHeaders.split('\n').find { header -> header.toLowerCase().startsWith('link:') }

  if (linkHeader) {
    def lastLink = linkHeader.split(',').find { link -> link.contains('rel="last"') }

    if (lastLink) {
      return lastLink[(lastLink.lastIndexOf('&page=')+6)..(lastLink.lastIndexOf('>')-1)]
    }
  }

  return "1"
}

// private
def getMatchingRepos(curlAuth, githubOwner, repositoryPrefix) {
  def githubReposUrl = "https://api.github.com/users/$githubOwner/repos?per_page=100"

  def curlHeaders = sh returnStdout: true, script: "$curlAuth --head $githubReposUrl"
  def numPages = getNumPages(curlHeaders)

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

  return matchingRepos
}

// private
def reportSecrets(secretMessages, repo, channel) {
  secretMessages.each { echo "$it" }

  if (channel.length() == 0) return

  slackSend channel: channel,
            color: "#ff0000",
            message: "POTENTIAL SECRETS DETECTED IN $repo\n$JOB_NAME/$BUILD_NUMBER\n(<$BUILD_URL|Open>)"
}

// public
def scanWithinWindow(credentialId, dockerImgName, githubOwner, repositoryPrefix, scanWindowHrs, slackChannel="") {
  withCredentials([string(credentialsId: credentialId, variable: 'githubToken')]) {
    def curlAuth = "curl --header 'Authorization: token $githubToken' --silent"

    def matchingRepos = getMatchingRepos(curlAuth, githubOwner, repositoryPrefix)
    def commitCheckDate = getCommitCheckDate(scanWindowHrs)

    echo "Commit check date: $commitCheckDate"

    def secretsFound = false

    matchingRepos.each { repo ->
      echo "Scanning $repo"

      // We don't handle more than 100 branches on a repo. You shouldn't have more than 100 branches open.
      def githubBranchUrl = "https://api.github.com/repos/$repo/branches?per_page=100"
      def branchResults = sh returnStdout: true, script: "$curlAuth $githubBranchUrl"
      def branches = readJSON text: branchResults
      def commitShas = []

      branches.each { branch ->
        def githubCommitUrl = "https://api.github.com/repos/$repo/commits?since=$commitCheckDate\\&sha=${branch.name}\\&per_page=100"
        def curlHeaders = sh returnStdout: true, script: "$curlAuth --head $githubCommitUrl"
        def numPages = getNumPages(curlHeaders)

        (numPages as Integer).times { page ->
          def commitResults = sh returnStdout: true, script: "$curlAuth $githubCommitUrl\\&page=${page+1}"
          def commits = readJSON text: commitResults
          commits.each { commit -> commitShas.add(commit.sha) }
        }
      }

      if (commitShas.size() > 0) {
        def secretMessages = runTruffleHog(dockerImgName, repo, commitShas)

        if (!secretMessages.isEmpty()) {
          secretsFound = true
          reportSecrets(secretMessages, repo, slackChannel)
        }
      }

      echo "Finished scanning $repo"
    }

    return secretsFound
  }
}

// public
def scanFullHistory(githubCredentialId, dockerImgName, githubOwner, repositoryPrefix, slackChannel="") {
  withCredentials([string(credentialsId: githubCredentialId, variable: 'githubToken')]) {
    def curlAuth = "curl --header 'Authorization: token $githubToken' --silent"
    def matchingRepos = getMatchingRepos(curlAuth, githubOwner, repositoryPrefix)

    def secretsFound = false

    matchingRepos.each { repo ->
      echo "Scanning $repo"

      def secretMessages = runTruffleHog(dockerImgName, repo)

      if (!secretMessages.isEmpty()) {
        secretsFound = true
        reportSecrets(secretMessages, repo, slackChannel)
      }

      echo "Finished scanning $repo"
    }

    return secretsFound
  }
}