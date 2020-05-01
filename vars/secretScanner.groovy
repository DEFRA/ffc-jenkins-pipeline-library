// private
@NonCPS // Don't run this in the Jenkins sandbox so that use (groovy.time.TimeCategory) will work
def getCommitCheckDate(scanWindowHrs) {
  use (groovy.time.TimeCategory) {
    return new Date() - scanWindowHrs.hours
  }
}

// private
def runTruffleHog(dockerImgName, repoName, commitCheckDate=new Date(0)) {
  // truffleHog seems to alway exit with code 1 even though it appears to run fine
  // which fails the build so we need the || true to ignore the exit code and carry on
  def truffleHogCmd = "docker run $dockerImgName --json --regex https://github.com/${repoName}.git || true"
  def truffleHogResults = sh returnStdout: true, script: truffleHogCmd
  def secretMessages = []

  truffleHogResults.trim().split('\n').each {
    if (it.length() == 0) return  // readJSON won't accept an empty string
    def result = readJSON text: it
    def commitDate = new Date().parse("yyyy-MM-dd HH:mm:ss", result.date)

    if (commitDate > commitCheckDate) {
      def message = "Reason: $result.reason\n" +
                    "Date: $result.date\n" +
                    "Repo: $repoName\n" +
                    "Branch: $result.branch\n" +
                    "Hash: $result.commitHash\n" +
                    "Filepath: $result.path\n" +
                    "StringsFound: $result.stringsFound\n" +
                    "Commit: $result.commit"

      secretMessages.add(message)
    }
  }

  return secretMessages
}

// private
def getMatchingRepos(curlAuth, githubOwner, repositoryPrefix) {
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

  return matchingRepos
}

def sendSlackMessage(channel, repo) {
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
    def commitCheckDateStr = commitCheckDate.format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))

    echo "Commit check date: $commitCheckDateStr"

    def secretsFound = false

    matchingRepos.each { repo ->
      echo "Scanning $repo"

      // We don't handle more than 100 branches on a repo. You shouldn't have more than 100 branches open.
      def githubBranchUrl = "https://api.github.com/repos/$repo/branches?per_page=100"
      def branchResults = sh returnStdout: true, script: "$curlAuth $githubBranchUrl"
      def branches = readJSON text: branchResults
      def commitExists = false

      for (branch in branches) {
        def githubCommitUrl = "https://api.github.com/repos/$repo/commits?since=$commitCheckDateStr\\&sha=${branch.name}"
        def commitResults = sh returnStdout: true, script: "$curlAuth $githubCommitUrl"
        def commits = readJSON text: commitResults

        if (commits.size() > 0) {
          commitExists = true
          break
        }
      }

      if (commitExists) {
        def secretMessages = runTruffleHog(dockerImgName, repo, commitCheckDate)
        secretsFound = !secretMessages.isEmpty()

        if (secretMessages.size() > 0) {
          sendSlackMessage(slackChannel, repo)

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
def scanFullHistory(githubCredentialId, dockerImgName, githubOwner, repositoryPrefix, slackChannel="") {
  withCredentials([string(credentialsId: githubCredentialId, variable: 'githubToken')]) {
    def curlAuth = "curl --header 'Authorization: token $githubToken' --silent"
    def matchingRepos = getMatchingRepos(curlAuth, githubOwner, repositoryPrefix)

    def secretsFound = false

    matchingRepos.each { repo ->
      echo "Scanning $repo"

      def secretMessages = runTruffleHog(dockerImgName, repo)
      secretsFound = !secretMessages.isEmpty()

      if (secretMessages.size() > 0) {
        sendSlackMessage(slackChannel, repo)
      }

      secretMessages.each {
        echo "$it"
      }

      echo "Finished scanning $repo"
    }

    return secretsFound
  }
}