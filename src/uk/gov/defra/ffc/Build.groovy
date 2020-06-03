package uk.gov.defra.ffc

import uk.gov.defra.ffc.Utils

class Build implements Serializable {
  /**
   * If the build is a branch with a Pull Request (PR), or the master branch a
   * message will be `echoed` describing the type of build.
   *
   * If the build is not for a PR or the master branch an error will be thrown with
   * the message `Build aborted - not a PR or a master branch`
   */
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

  static def buildTestImage(ctx, credentialsId, registry, projectName, buildNumber, tag) {
    ctx.docker.withRegistry("https://$registry", credentialsId) {
      ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml build --no-cache")
    }
  }

  static def getVariables(ctx, version) {
    def branch = ctx.BRANCH_NAME
    def repoName = Utils.getRepoName(ctx)

    // use the git API to get the open PR for a branch
    // Note: This will cause issues if one branch has two open PRs
    def pr = ctx.sh(returnStdout: true, script: "curl https://api.github.com/repos/DEFRA/$repoName/pulls?state=open | jq '.[] | select(.head.ref == \"$branch\") | .number'").trim()
    Build.verifyCommitBuildable(ctx, pr)

    def tag

    if (branch == 'master') {
      tag = version
    } else {
      def rawTag = pr ? "pr$pr" : branch
      tag = rawTag.replaceAll(/[^a-zA-Z0-9]/, '-').toLowerCase()
    }

    def mergedPrNo = Utils.getMergedPrNo(ctx)
    def repoUrl = Utils.getRepoUrl(ctx)
    return [repoName, pr, tag, mergedPrNo]
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

  static def npmAudit(ctx, auditLevel, logType) {
    auditLevel = auditLevel ?: 'moderate'
    ctx.sh(returnStatus: true, script: "npm audit --audit-level=$auditLevel --$logType")
  }
}
