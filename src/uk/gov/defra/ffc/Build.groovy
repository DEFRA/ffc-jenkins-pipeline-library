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
      ctx.env.PR_BUILD = true
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

  static def getNodeTestVersion(defaultNodeTestVersion, requestedNodeTestVersion) {
    return requestedNodeTestVersion != null ? requestedNodeTestVersion : defaultNodeTestVersion
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

  static def snykTest(ctx, failOnIssues, organisation, severity, targetFile, pr) {
    failOnIssues = shouldFailOnIssues(failOnIssues, pr)
    organisation = organisation ?: ctx.SNYK_ORG
    severity = severity ?: 'medium'

    ctx.sh('mkdir -p -m 777 snyk-cli')
    ctx.dir('snyk-cli') {
      ctx.withCredentials([ctx.string(credentialsId: 'ffc-snyk-token', variable: 'snykToken')
      ]) {
          ctx.echo("SNYK TOKEN: $ctx.snykToken")
          def script = "docker run -e 'SNYK_TOKEN=$ctx.snykToken' -e 'MONITOR=true' -v '$ctx.WORKSPACE:/project' snyk/snyk-cli:npm test --json > snyk-result.json --org=$organisation --severity-threshold=$severity --file=$targetFile"
          ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.SnykTest.Context, description: GitHubStatus.SnykTest.Description) {
            ctx.sh(returnStatus: !failOnIssues, script: script)
          }
        }
      }
  }

  static void triggerMultiBranchBuilds(def ctx, String defaultBranch) {
    String multiBranchJob = jobPath.substring(0, ctx.JOB_NAME.lastIndexOf('/'))
    def item = jenkins.model.Jenkins.get().getItemByFullName(multiBranchJob)
    def jobNames = item.allJobs.collect {it.fullName}
    item = null // CPS -- remove reference to non-serializable object
    for (jobName in jobNames) {
      String branchName = jobName.substring(jobName.lastIndexOf('/'))
      ctx.echo(branchName)
      if(branchName != defaultBranch)
        // build job: jobName
        ctx.echo(jobName)
    }
  }
}
