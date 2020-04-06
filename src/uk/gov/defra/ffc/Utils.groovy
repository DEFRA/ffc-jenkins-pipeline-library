package uk.gov.defra.ffc

class Utils {
  static def getRepoUrl() {
    return sh(returnStdout: true, script: "git config --get remote.origin.url").trim()
  }

  static def getRepoName() {
    return scm.getUserRemoteConfigs()[0].getUrl().tokenize('/').last().split("\\.git")[0]
  }

  static def getCommitSha() {
    return sh(returnStdout: true, script: "git rev-parse HEAD").trim()
  }

  static def getMergedPrNo() {
    def mergedPrNo = sh(returnStdout: true, script: "git log --pretty=oneline --abbrev-commit -1 | sed -n 's/.*(#\\([0-9]\\+\\)).*/\\1/p'").trim()
    return mergedPrNo ? "pr$mergedPrNo" : ''
  }
}
