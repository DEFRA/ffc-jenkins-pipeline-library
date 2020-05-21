import uk.gov.defra.ffc.SecretScanner

// private
@NonCPS // Don't run this in the Jenkins sandbox so that use (groovy.time.TimeCategory) will work
def getCommitCheckDate(scanWindowHrs) {
  use (groovy.time.TimeCategory) {
    return (new Date() - scanWindowHrs.hours - 10.minutes).format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))
  }
}

// private
def runTruffleHog(dockerImgName, repoName, excludeStrings, commitShas=null) {
  return SecretScanner.runTruffleHog(this, dockerImgName, repoName, excludeStrings, commitShas=null)
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

  def message = "POTENTIAL SECRETS DETECTED IN $repo\n$JOB_NAME/$BUILD_NUMBER\n(<$BUILD_URL|Open>)"

  notifySlack.sendMessage(channel, message, true)
}

def scanWithinWindow(githubCredentialId, dockerImgName, githubOwner, repositoryPrefix, scanWindowHrs, excludeStrings, slackChannel="") {
  return SecretScanner.scanWithinWindow(this, githubCredentialId, dockerImgName, githubOwner, repositoryPrefix, scanWindowHrs, excludeStrings, slackChannel)
}

// public
def scanFullHistory(githubCredentialId, dockerImgName, githubOwner, repositoryPrefix, excludeStrings, slackChannel="") {
  withCredentials([string(credentialsId: githubCredentialId, variable: 'githubToken')]) {
    def curlAuth = "curl --header 'Authorization: token $githubToken' --silent"
    def matchingRepos = getMatchingRepos(curlAuth, githubOwner, repositoryPrefix)

    def secretsFound = false

    matchingRepos.each { repo ->
      echo "Scanning $repo"

      def secretMessages = runTruffleHog(dockerImgName, repo, excludeStrings)

      if (!secretMessages.isEmpty()) {
        secretsFound = true
        reportSecrets(secretMessages, repo, slackChannel)
      }

      echo "Finished scanning $repo"
    }

    return secretsFound
  }
}
