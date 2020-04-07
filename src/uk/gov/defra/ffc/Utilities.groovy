package uk.gov.defra.ffc

class Utilities {
  def context
  Utilities(context) {
    this.context = context
  }

  def getRepoUrl() {
    return context.sh(returnStdout: true, script: "git config --get remote.origin.url").trim()
  }

  def getRepoName() {
    return context.scm.getUserRemoteConfigs()[0].getUrl().tokenize('/').last().split("\\.git")[0]
  }

  def getCommitSha() {
    return context.sh(returnStdout: true, script: "git rev-parse HEAD").trim()
  }

  def getMergedPrNo() {
    def mergedPrNo = context.sh(returnStdout: true, script: "git log --pretty=oneline --abbrev-commit -1 | sed -n 's/.*(#\\([0-9]\\+\\)).*/\\1/p'").trim()
    return mergedPrNo ? "pr$mergedPrNo" : ''
  }

  def getVersion() {
    return context.sh(returnStdout: true, script: "jq -r '.version' package.json").trim()
  }

  def getVariables() {
    def branch = BRANCH_NAME
    context.echo "***************branch: $branch"
    def repoName = this.getRepoName()
    def version = this.getVersion()
    // use the git API to get the open PR for a branch
    // Note: This will cause issues if one branch has two open PRs
    def pr = context.sh(returnStdout: true, script: "curl https://api.github.com/repos/DEFRA/$repoName/pulls?state=open | jq '.[] | select(.head.ref == \"$branch\") | .number'").trim()
    verifyCommitBuildable()

    def containerTag
    if (branch == "master") {
      containerTag = version
    } else {
      def rawTag = pr == '' ? branch : "pr$pr"
      containerTag = rawTag.replaceAll(/[^a-zA-Z0-9]/, '-').toLowerCase()
    }

    def mergedPrNo = this.getMergedPrNo()
    def repoUrl = this.getRepoUrl()
    def commitSha = this.getCommitSha()
    return [repoName, pr, containerTag, mergedPrNo]
  }
}
