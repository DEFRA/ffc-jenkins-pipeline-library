package uk.gov.defra.ffc

class Utilities implements Serializable {
  def branch
  def containerTag
  def context
  def mergedPrNo
  def pr
  def repoName
  def version

  Utilities(context) {
    this.context = context
    this.branch = this.context.BRANCH_NAME
    this.context.echo "BRANCH_NAME: ${this.branch}"
    this.verifyCommitBuildable();

    this.containerTag = this.getContainerTag()
    this.mergedPrNo = this.getMergedPrNo()
    this.pr = this.getPr()
    this.repoName = this.getRepoName()
    this.version = this.getVersion()
  }

  def getPr() {
    // use the git API to get the open PR for a branch
    // Note: This will cause issues if one branch has two open PRs
    return context
      .sh(returnStdout: true,
          script: "curl https://api.github.com/repos/DEFRA/$repoName/pulls?state=open | jq '.[] | select(.head.ref == \"$branch\") | .number'")
      .trim()
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

  @NonCPS
  def verifyCommitBuildable() {
    if (this.pr) {
      echo "Building PR$pr"
    } else if (this.branch == "master") {
      echo "Building master branch"
    } else {
      this.context.currentBuild.result = 'ABORTED'
      this.context.error('Build aborted - not a PR or a master branch')
    }
  }

  def getContainerTag() {
    def containerTag
    if (this.branch == "master") {
      containerTag = this.version
    } else {
      def rawTag = this.pr == '' ? this.branch : "pr$pr"
      containerTag = rawTag.replaceAll(/[^a-zA-Z0-9]/, '-').toLowerCase()
    }
    return containerTag
  }

  def getVariables() {
    return [this.repoName, this.pr, this.containerTag, this.mergedPrNo]
  }

  def updateGithubCommitStatus(message, state) {
    def repoUrl = this.getRepoUrl()
    def repoName = this.getRepoName()
    def commitSha = this.getCommitSha()

    step([
      $class: 'GitHubCommitStatusSetter',
      reposSource: [$class: "ManuallyEnteredRepositorySource", url: repoUrl],
      commitShaSource: [$class: "ManuallyEnteredShaSource", sha: commitSha],
      errorHandlers: [[$class: 'ShallowAnyErrorHandler']],
      statusResultSource: [ $class: "ConditionalStatusResultSource", results: [[$class: "AnyBuildResult", message: message, state: state]] ]
    ])
  }
}
