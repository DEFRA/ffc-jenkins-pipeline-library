package uk.gov.defra.ffc

import uk.gov.defra.ffc.Utils

class Build implements Serializable {
  private static def verifyCommitBuildable(ctx, pr) {
    if (pr) {
      ctx.echo("Building PR$pr")
    } else if (ctx.BRANCH_NAME == 'master') {
      ctx.echo('Building master branch')
    } else {
      ctx.currentBuild.result = 'ABORTED'
      ctx.error('Build aborted - not a PR or a master branch')
    }
  }

  static def getVariables(ctx, version) {
    def branch = ctx.BRANCH_NAME
    def repoName = Utils.getRepoName(ctx)

    // use the git API to get the open PR for a branch
    // Note: This will cause issues if one branch has two open PRs
    def pr = ctx.sh(returnStdout: true, script: "curl https://api.github.com/repos/DEFRA/$repoName/pulls?state=open | jq '.[] | select(.head.ref == \"$branch\") | .number'").trim()
    Build.verifyCommitBuildable(ctx, pr)

    def identityTag

    if (branch == 'master') {
      identityTag = version
    } else {
      def rawTag = pr ? "pr$pr" : branch
      identityTag = rawTag.replaceAll(/[^a-zA-Z0-9]/, '-').toLowerCase()
    }

    def mergedPrNo = Utils.getMergedPrNo(ctx)
    def repoUrl = Utils.getRepoUrl(ctx)
    return [repoName, pr, identityTag, mergedPrNo]
  }

  static def updateGithubCommitStatus(ctx, message, state) {
    def commitSha = Utils.getCommitSha(ctx)
    def repoUrl = Utils.getRepoUrl(ctx)
    ctx.step([
      $class: 'GitHubCommitStatusSetter',
      reposSource: [$class: 'ManuallyEnteredRepositorySource', url: repoUrl],
      commitShaSource: [$class: 'ManuallyEnteredShaSource', sha: commitSha],
      errorHandlers: [[$class: 'ShallowAnyErrorHandler']],
      statusResultSource: [ $class: 'ConditionalStatusResultSource', results: [[$class: 'AnyBuildResult', message: message, state: state]] ]
    ])
  }
}
