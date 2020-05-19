package uk.gov.defra.ffc

class Utils implements Serializable {
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

  static def getMergedPrNo(ctx) {
    def mergedPrNo = ctx.sh(returnStdout: true, script: "git log --pretty=oneline --abbrev-commit -1 | sed -n 's/.*(#\\([0-9]\\+\\)).*/\\1/p'").trim()
    return mergedPrNo ? "pr$mergedPrNo" : ''
  }

  static def getRepoUrl(ctx) {
    return ctx.sh(returnStdout: true, script: 'git config --get remote.origin.url').trim()
  }

  static def getFolder(repoName) {
    def folderArray = repoName.split('-')
    return "${folderArray[0]}-${folderArray[1]}"
  }
}
