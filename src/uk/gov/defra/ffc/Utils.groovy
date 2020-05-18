package uk.gov.defra.ffc

class Utils implements Serializable {
  static def replaceInFile(ctx, from, to, file) {
    ctx.sh("sed -i -e 's/$from/$to/g' $file")
  }

  static def getCommitMessage(ctx) {
    return ctx.sh(returnStdout: true, script: 'git log -1 --pretty=%B | cat')
  }

  static def getCommitSha(ctx) {
    return ctx.sh(returnStdout: true, script: "git rev-parse HEAD").trim()
  }

  static def generatePrNames(dbName, prCode) {
    def prSchema = "pr$prCode"
    def prUser = "${dbName}_$prSchema"
    return [prSchema, prUser]
  }
}
