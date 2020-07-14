package uk.gov.defra.ffc

import uk.gov.defra.ffc.GitHubStatus
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

  static def checkoutSourceCode(ctx) {
    ctx.checkout(ctx.scm)
    ctx.sh("git remote set-branches --add origin master")
    ctx.sh("git fetch")
  }

  static def buildTestImage(ctx, credentialsId, registry, projectName, buildNumber, tag) {
    ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.BuildTestImage.Context, description: GitHubStatus.BuildTestImage.Description) {
      ctx.docker.withRegistry("https://$registry", credentialsId) {
        ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml build")
      }
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
    ctx.step([
      $class: 'GitHubCommitStatusSetter',
      reposSource: [$class: 'ManuallyEnteredRepositorySource', url: Utils.getRepoUrl(ctx)],
      contextSource: [$class: "ManuallyEnteredCommitContextSource", context: GitHubStatus.Build.Context],
      commitShaSource: [$class: 'ManuallyEnteredShaSource', sha: Utils.getCommitSha(ctx)],
      errorHandlers: [[$class: 'ShallowAnyErrorHandler']],
      statusResultSource: [ $class: 'ConditionalStatusResultSource', results: [[$class: 'AnyBuildResult', message: message, state: state]] ]
    ])
  }

  static def npmAudit(ctx, auditLevel, logType, failOnIssues, containerImage, containerWorkDir) {
    auditLevel = auditLevel ?: 'moderate'
    logType = logType ?: 'parseable'
    failOnIssues = failOnIssues == false ? false : true
    // setting `returnStatus` means the sh cmd can return non-zero exit codes
    // without affecting the build status
    def script = "docker run --rm -u node " +
    "--mount type=bind,source='$ctx.WORKSPACE/package.json',target=$containerWorkDir/package.json " +
    "--mount type=bind,source='$ctx.WORKSPACE/package-lock.json',target=$containerWorkDir/package-lock.json " +
    "$containerImage npm audit --audit-level=$auditLevel --$logType"
    ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.NpmAudit.Context, description: GitHubStatus.NpmAudit.Description) {
      ctx.sh(returnStatus: !failOnIssues, script: script)
    }
  }

  static def extractSynkFiles(ctx, projectName) {
    ctx.sh('docker-compose -f docker-compose.snyk.yaml up')
  }

  static def snykTest(ctx, failOnIssues, organisation, severity, targetFile) {
    failOnIssues = failOnIssues == false ? false : true
    organisation = organisation ?: ctx.SNYK_ORG
    severity = severity ?: 'medium'
    ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.SnykTest.Context, description: GitHubStatus.SnykTest.Description) {
      ctx.snykSecurity(snykInstallation: 'snyk-default', snykTokenId: 'snyk-token', failOnIssues: failOnIssues, organisation: organisation, severity: severity, targetFile: targetFile)
    }
  }
}
