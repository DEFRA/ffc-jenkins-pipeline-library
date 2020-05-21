package uk.gov.defra.ffc

import uk.gov.defra.ffc.Notifications

class SecretScanner implements Serializable {
  private static def runTruffleHog(ctx, dockerImgName, repoName, excludeStrings, commitShas=null) {
    // truffleHog seems to alway exit with code 1 even though it appears to run fine
    // which fails the build so we need the || true to ignore the exit code and carry on
    def truffleHogCmd = "docker run $dockerImgName --json https://github.com/${repoName}.git || true"
    def truffleHogResults = ctx.sh(returnStdout: true, script: truffleHogCmd)
    def secretMessages = []

    truffleHogResults.trim().split('\n').each {
      if (it.length() == 0) return  // readJSON won't accept an empty string
      def result = ctx.readJSON(text: it)

      if (!commitShas || commitShas.contains(result.commitHash)) {
        if (!result.stringsFound.every { it in excludeStrings }) {
          def message = "Reason: $result.reason\n" +
                        "Date: $result.date\n" +
                        "Repo: $repoName\n" +
                        "Branch: $result.branch\n" +
                        "Hash: $result.commitHash\n" +
                        "File path: $result.path\n" +
                        "Strings found: $result.stringsFound\n" +
                        "Commit link: https://github.com/$repoName/commit/$result.commitHash\n" +
                        "File link: https://github.com/$repoName/blob/$result.commitHash/$result.path\n"

          secretMessages.add(message)
        }
      }
    }

    return secretMessages
  }

  private static def getMatchingRepos(ctx, curlAuth, githubOwner, repositoryPrefix) {
    def githubReposUrl = "https://api.github.com/users/$githubOwner/repos?per_page=100"

    def curlHeaders = ctx.sh(returnStdout: true, script: "$curlAuth --head $githubReposUrl")
    def numPages = SecretScanner.getNumPages(ctx, curlHeaders)

    ctx.echo("Number of pages of repos: $numPages")

    def matchingRepos = []
    def matchStr = "$githubOwner/$repositoryPrefix".toLowerCase()

    (numPages as Integer).times { page ->
      def reposResults = ctx.sh(returnStdout: true, script: "$curlAuth $githubReposUrl\\&page=${page+1}")
      def allRepos = ctx.readJSON(text: reposResults)

      allRepos.each { repo ->
        if (repo.full_name.toLowerCase().startsWith(matchStr)) {
          matchingRepos.add(repo.full_name)
        }
      }
    }

    ctx.echo("Matching repos: $matchingRepos")

    return matchingRepos
  }

  private static def getNumPages(ctx, curlHeaders) {
    def linkHeader = curlHeaders.split('\n').find { header -> header.toLowerCase().startsWith('link:') }

    if (linkHeader) {
      def lastLink = linkHeader.split(',').find { link -> link.contains('rel="last"') }

      if (lastLink) {
        return lastLink[(lastLink.lastIndexOf('&page=')+6)..(lastLink.lastIndexOf('>')-1)]
      }
    }

    return '1'
  }

  private static def reportSecrets(ctx, secretMessages, repo, channel) {
    secretMessages.each { ctx.echo("$it") }

    if (channel.length() == 0) return

    def message = "POTENTIAL SECRETS DETECTED IN $repo\n$ctx.JOB_NAME/$ctx.BUILD_NUMBER\n(<$ctx.BUILD_URL|Open>)"

    Notifications.sendMessage(ctx, channel, message, true)
  }

  @NonCPS // Don't run this in the Jenkins sandbox so that use (groovy.time.TimeCategory) will work
  private static def getCommitCheckDate(scanWindowHrs) {
    use (groovy.time.TimeCategory) {
      return (new Date() - scanWindowHrs.hours - 10.minutes).format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))
    }
  }

  static def scanWithinWindow(ctx, githubCredentialId, dockerImgName, githubOwner, repositoryPrefix, scanWindowHrs, excludeStrings, slackChannel="") {
    ctx.withCredentials([ctx.string(credentialsId: githubCredentialId, variable: 'githubToken')]) {
      def curlAuth = "curl --header 'Authorization: token $ctx.githubToken' --silent"

      def matchingRepos = SecretScanner.getMatchingRepos(ctx, curlAuth, githubOwner, repositoryPrefix)
      def commitCheckDate = SecretScanner.getCommitCheckDate(scanWindowHrs)

      ctx.echo("Commit check date: $commitCheckDate")

      def secretsFound = false

      matchingRepos.each { repo ->
        ctx.echo("Scanning $repo")

        // We don't handle more than 100 branches on a repo. You shouldn't have more than 100 branches open.
        def githubBranchUrl = "https://api.github.com/repos/$repo/branches?per_page=100"
        def branchResults = ctx.sh(returnStdout: true, script: "$curlAuth $githubBranchUrl")
        def branches = ctx.readJSON(text: branchResults)
        def commitShas = []

        branches.each { branch ->
          def githubCommitUrl = "https://api.github.com/repos/$repo/commits?since=$commitCheckDate\\&sha=${branch.name}\\&per_page=100"
          def curlHeaders = ctx.sh(returnStdout: true, script: "$curlAuth --head $githubCommitUrl")
          def numPages = SecretScanner.getNumPages(ctx, curlHeaders)

          (numPages as Integer).times { page ->
            def commitResults = ctx.sh(returnStdout: true, script: "$curlAuth $githubCommitUrl\\&page=${page+1}")
            def commits = ctx.readJSON(text: commitResults)
            commits.each { commit -> commitShas.add(commit.sha) }
          }
        }

        if (commitShas.size() > 0) {
          def secretMessages = SecretScanner.runTruffleHog(ctx, dockerImgName, repo, excludeStrings, commitShas)

          if (!secretMessages.isEmpty()) {
            secretsFound = true
            SecretScanner.reportSecrets(ctx, secretMessages, repo, slackChannel)
          }
        }

        ctx.echo("Finished scanning $repo")
      }

      return secretsFound
    }
  }

  static def scanFullHistory(ctx, githubCredentialId, dockerImgName, githubOwner, repositoryPrefix, excludeStrings, slackChannel="") {
    ctx.withCredentials([ctx.string(credentialsId: githubCredentialId, variable: 'githubToken')]) {
      def curlAuth = "curl --header 'Authorization: token $ctx.githubToken' --silent"
      def matchingRepos = SecretScanner.getMatchingRepos(ctx, curlAuth, githubOwner, repositoryPrefix)

      def secretsFound = false

      matchingRepos.each { repo ->
        ctx.echo("Scanning $repo")

        def secretMessages = SecretScanner.runTruffleHog(ctx, dockerImgName, repo, excludeStrings)

        if (!secretMessages.isEmpty()) {
          secretsFound = true
          SecretScanner.reportSecrets(ctx, secretMessages, repo, slackChannel)
        }

        ctx.echo("Finished scanning $repo")
      }

      return secretsFound
    }
  }
}
