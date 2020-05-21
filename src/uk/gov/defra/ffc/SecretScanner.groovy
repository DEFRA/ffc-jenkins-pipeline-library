package uk.gov.defra.ffc

class SecretScanner implements Serializable {
  static def runTruffleHog(ctx, dockerImgName, repoName, excludeStrings, commitShas=null) {
    // truffleHog seems to alway exit with code 1 even though it appears to run fine
    // which fails the build so we need the || true to ignore the exit code and carry on
    def truffleHogCmd = "docker run $dockerImgName --json https://github.com/${repoName}.git || true"
    def truffleHogResults = ctx.sh(returnStdout: true, script: truffleHogCmd)
    def secretMessages = []

    truffleHogResults.trim().split('\n').each {
      if (it.length() == 0) return  // readJSON won't accept an empty string
      def result = ctx.readJSON text: it

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
}
