package uk.gov.defra.ffc

class Utils implements Serializable {
  static String suppressConsoleOutput = '#!/bin/bash +x\n'
  
  static def replaceInFile(ctx, from, to, file) {
    ctx.sh("sed -i -e 's/$from/$to/g' $file")
  }

  static def getCommitMessage(ctx) {
    return ctx.sh(returnStdout: true, script: 'git log -1 --pretty=%B | cat')
  }

  static def getCommitSha(ctx) {
    return ctx.sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
  }

  static def generatePrNames(dbName, prCode) {
    def prSchema = "pr$prCode"
    def prUser = "${dbName}_$prSchema"
    return [prSchema, prUser]
  }

  static def getRepoName(ctx) {
    return ctx.scm.getUserRemoteConfigs()[0].getUrl().tokenize('/').last().split('\\.git')[0]
  }

  /**
   * Parses the local commit log to obtain the merged PR number from the message.
   * This is reliant on the standard GitHub merge message of the PR name followed by
   * the PR number, i.e. `Update license details (#53)`
   *
   * The method returns the PR number for a merge of the appropriate format, i.e.
   * `pr53` or an empty string if not.
   */
  static def getMergedPrNo(ctx) {
    def mergedPrNo = ctx.sh(returnStdout: true, script: "git log --pretty=oneline --abbrev-commit -1 | sed -n 's/.*(#\\([0-9]\\+\\)).*/\\1/p'").trim()
    return mergedPrNo ? "pr$mergedPrNo" : ''
  }

  /**
    * Obtains the remote URL of the current repository, i.e.
    * `https://github.com/DEFRA/ffc-demo-web.git`
   */
  static def getRepoUrl(ctx) {
    return ctx.sh(returnStdout: true, script: 'git config --get remote.origin.url').trim()
  }

  static def getFolder(repoName) {
    def folderArray = repoName.split('-')
    return "${folderArray[0]}-${folderArray[1]}"
  }

  static def getErrorMessage(e) {
    def errMessage = e.message
    if (!errMessage) {
      def errCauses = e.getCauses()
      if (errCauses) {
        def errMessages = []
        errCauses.each { errCause ->
          if (errCause instanceof io.snyk.jenkins.workflow.FoundIssuesCause) {
            errMessages.add(errCause.getShortDescription())
          }
        }
        errMessage = errMessages.join(', ')
      }
      errMessage = errMessage ?: 'No error message available.'
    }
    return errMessage
  }
}
