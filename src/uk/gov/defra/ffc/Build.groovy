package uk.gov.defra.ffc

import uk.gov.defra.ffc.Utils

class Build implements Serializable {
  static def getRepoName(ctx) {
    return ctx.scm.getUserRemoteConfigs()[0].getUrl().tokenize('/').last().split("\\.git")[0]
  }

  static def verifyCommitBuildable(ctx, pr) {
    if (pr) {
      ctx.echo("Building PR$pr")
    } else if (ctx.BRANCH_NAME == "master") {
      ctx.echo("Building master branch")
    } else {
      ctx.currentBuild.result = 'ABORTED'
      ctx.error('Build aborted - not a PR or a master branch')
    }
  }

  static def getMergedPrNo(ctx) {
    // possible super ternary
    def mergedPrNo = ctx.sh(returnStdout: true, script: "git log --pretty=oneline --abbrev-commit -1 | sed -n 's/.*(#\\([0-9]\\+\\)).*/\\1/p'").trim()
    return mergedPrNo ? "pr$mergedPrNo" : ''
  }

  static def getRepoUrl(ctx) {
    return ctx.sh(returnStdout: true, script: "git config --get remote.origin.url").trim()
  }

  static def getVariables(ctx) {
    def branch = ctx.BRANCH_NAME
    def repoName = Build.getRepoName(ctx)

    // use the git API to get the open PR for a branch
    // Note: This will cause issues if one branch has two open PRs
    def pr = ctx.sh(returnStdout: true, script: "curl https://api.github.com/repos/DEFRA/$repoName/pulls?state=open | jq '.[] | select(.head.ref == \"$branch\") | .number'").trim()
    Build.verifyCommitBuildable(ctx, pr)

    def identityTag

    if (branch == "master") {
      identityTag = ctx.version
    } else {
      def rawTag = pr == '' ? branch : "pr$pr"
      identityTag = rawTag.replaceAll(/[^a-zA-Z0-9]/, '-').toLowerCase()
    }

    def mergedPrNo = Build.getMergedPrNo(ctx)
    def repoUrl = Build.getRepoUrl(ctx)
    return [repoName, pr, identityTag, mergedPrNo]
  }

  static def updateGithubCommitStatus(ctx, message, state) {
    def commitSha = Utils.getCommitSha(ctx)
    def repoUrl = Build.getRepoUrl(ctx)
    ctx.step([
      $class: 'GitHubCommitStatusSetter',
      reposSource: [$class: "ManuallyEnteredRepositorySource", url: repoUrl],
      commitShaSource: [$class: "ManuallyEnteredShaSource", sha: commitSha],
      errorHandlers: [[$class: 'ShallowAnyErrorHandler']],
      statusResultSource: [ $class: "ConditionalStatusResultSource", results: [[$class: "AnyBuildResult", message: message, state: state]] ]
    ])
  }
}
