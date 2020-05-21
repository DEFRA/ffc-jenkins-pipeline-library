package uk.gov.defra.ffc

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

  static def scanWithinWindow(ctx, githubCredentialId, dockerImgName, githubOwner, repositoryPrefix, scanWindowHrs, excludeStrings, slackChannel="") {
    ctx.withCredentials([ctx.string(credentialsId: githubCredentialId, variable: 'githubToken')]) {
      def curlAuth = "curl --header 'Authorization: token $ctx.githubToken' --silent"

      def matchingRepos = getMatchingRepos(curlAuth, githubOwner, repositoryPrefix)
      def commitCheckDate = getCommitCheckDate(scanWindowHrs)

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
          def numPages = getNumPages(curlHeaders)

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
            reportSecrets(secretMessages, repo, slackChannel)
          }
        }

        ctx.echo("Finished scanning $repo")
      }

      return secretsFound
    }
  }
}
