package uk.gov.defra.ffc

import uk.gov.defra.ffc.GitHubStatus

class Build implements Serializable {
  /**
   * If the build is a branch with a Pull Request (PR), or the main branch a
   * message will be `echoed` describing the type of build.
   *
   * If the build is not for a PR or the main branch an error will be thrown with
   * the message `Build aborted - not a PR or a main branch`
   */
  private static def verifyCommitBuildable(ctx, pr, defaultBranch) {
    if (pr) {
      ctx.echo("Building PR$pr")
    } else if (ctx.BRANCH_NAME == defaultBranch) {
      ctx.echo('Building main branch')
    } else {
      ctx.currentBuild.result = 'ABORTED'
      ctx.error('Build aborted - not a PR or a main branch')
    }
  }

  static def getDefaultBranch(defaultBranch, requestedBranch) {
    return requestedBranch != null ? requestedBranch : defaultBranch
  }

  static def checkoutSourceCode(ctx, defaultBranch) {
    ctx.checkout(ctx.scm)
    ctx.sh("git remote set-branches --add origin ${defaultBranch}")
    ctx.sh("git fetch")
  }

  static def getVariables(ctx, version, defaultBranch) {
    def branch = ctx.BRANCH_NAME
    def repoName = Utils.getRepoName(ctx)

    // use the git API to get the open PR for a branch
    // Note: This will cause issues if one branch has two open PRs
    def pr = ctx.sh(returnStdout: true, script: "curl https://api.github.com/repos/DEFRA/$repoName/pulls?state=open | jq '.[] | select(.head.ref == \"$branch\") | .number'").trim()
    verifyCommitBuildable(ctx, pr, defaultBranch)

    def tag

    if (branch == defaultBranch) {
      tag = version
    } else {
      def rawTag = pr ? "pr$pr" : branch
      tag = rawTag.replaceAll(/[^a-zA-Z0-9]/, '-').toLowerCase()
    }

    def mergedPrNo = Utils.getMergedPrNo(ctx)
    def repoUrl = Utils.getRepoUrl(ctx)
    return [repoName, pr, tag, mergedPrNo]
  }

  static def shouldFailOnIssues(failOnIssues, pr) {
    def shouldFail = failOnIssues == false ? false : true
    // Override the flag if the build is a non-PR build
    if (pr == '') {
      shouldFail = false
    }
    return shouldFail
  }

  static def npmAudit(ctx, auditLevel, logType, failOnIssues, containerImage, containerWorkDir, pr) {
    auditLevel = auditLevel ?: 'moderate'
    logType = logType ?: 'parseable'
    failOnIssues = shouldFailOnIssues(failOnIssues, pr)
    // setting `returnStatus` means the sh cmd can return non-zero exit codes
    // without affecting the build status
    def script = "docker run --rm -u node " +
    "--mount type=bind,source='$ctx.WORKSPACE/package.json',target=$containerWorkDir/package.json " +
    "--mount type=bind,source='$ctx.WORKSPACE/package-lock.json',target=$containerWorkDir/package-lock.json " +
    "$containerImage npm audit --audit-level=$auditLevel --production --$logType"
    ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.NpmAudit.Context, description: GitHubStatus.NpmAudit.Description) {
      ctx.sh(returnStatus: !failOnIssues, script: script)
    }
  }

  static def extractSynkFiles(ctx, projectName, buildNumber, tag) {
    try {
      ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.snyk.yaml up")
    } finally {
      ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.snyk.yaml down -v")
    }
  }

  static def snykTest(ctx, failOnIssues, organisation, severity, targetFile, pr, containerWorkDir, repoName) {
    failOnIssues = shouldFailOnIssues(failOnIssues, pr)
    organisation = organisation ?: ctx.SNYK_ORG
    severity = severity ?: 'medium'    
    String token = Utils.getCommitSha(ctx)   
    
    /* ctx.withCredentials([ctx.string(credentialsId: 'github-auth-token', variable: 'githubToken')]) {    
      def script = "docker run -it -e 'SNYK_TOKEN=$ctx.githubToken' -e 'USER_ID=1234' -e 'MONITOR=true' -v '$containerWorkDir:/$repoName' snyk/snyk-cli:npm test --org=$organisation"
    } */
    
    /* ctx.withCredentials([ctx.string(credentialsId: 'ffc-snyk-token', variable: 'snykToken')
    ]) {
        ctx.echo("SNYK TOKEN: $ctx.snykToken")
        def script = "docker run -it -e 'SNYK_TOKEN=$ctx.snykToken' -e 'USER_ID=1234' -e 'MONITOR=true' -v '$containerWorkDir:/$repoName' snyk/snyk-cli:npm test --org=$organisation"
        ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.SnykTest.Context, description: GitHubStatus.SnykTest.Description) {
          ctx.sh(returnStatus: !failOnIssues, script: script)
        }
      } */
      
        def script = "docker run -e 'SNYK_TOKEN=cbdbcd2c-bf47-4d4b-9371-a9c17099fe65' -v '/home/node:/project' snyk/snyk-cli:npm test --org=$organisation"
        ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.SnykTest.Context, description: GitHubStatus.SnykTest.Description) {
          ctx.sh(returnStatus: !failOnIssues, script: script)
        }
      
  }
}
