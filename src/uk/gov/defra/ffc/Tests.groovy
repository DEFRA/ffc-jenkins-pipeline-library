package uk.gov.defra.ffc

import uk.gov.defra.ffc.GitHubStatus
import uk.gov.defra.ffc.Utils

class Tests implements Serializable {
  static def runTests(ctx, projectName, serviceName, buildNumber, tag, pr, environment) {
    ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.RunTests.Context, description: GitHubStatus.RunTests.Description) {
      if (ctx.fileExists('./docker-compose.test.yaml')) {
        try {
          ctx.sh('mkdir -p -m 777 test-output')
          if (ctx.fileExists('./docker-compose.migrate.yaml')) {
            ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.migrate.yaml run database-up")
          }
          ctx.withEnv(Provision.getBuildQueueEnvVars(ctx, serviceName, pr, environment)) {
            ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml run $serviceName")
          }
        } finally {
          ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml down -v")
          if (ctx.fileExists('./docker-compose.migrate.yaml')) {
            ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.migrate.yaml down -v")
          }
        }
      } else {
        ctx.echo("docker-compose.test.yaml not found, skipping test run")
      }
    }
  }

  static def runZapScan(ctx, projectName, buildNumber, tag) {
    def zapDockerComposeFile = 'docker-compose.zap.yaml'
    if (ctx.fileExists("./$zapDockerComposeFile")) {
      ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.ZapScan.Context, description: GitHubStatus.ZapScan.Description) {
        try {
          // test-output exists if stage is run after 'runTests', take no risks and create it
          ctx.sh('mkdir -p -m 666 test-output')
          ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.yaml -f $zapDockerComposeFile run -v /etc/ssl/certs/:/etc/ssl/certs/ -v /usr/local/share/ca-certificates/:/usr/local/share/ca-certificates/ zap-baseline-scan")
        } finally {
          ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.yaml -f $zapDockerComposeFile down -v")
        }
      }
    } else {
      ctx.echo("$zapDockerComposeFile not found, skipping test run")
    }
  }

  static def lintHelm(ctx, chartName) {
    ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.HelmLint.Context, description: GitHubStatus.HelmLint.Description) {
      Helm.addHelmRepo(ctx, 'ffc-public', ctx.HELM_CHART_REPO_PUBLIC)
      ctx.sh("helm dependency update ./helm/$chartName")
      ctx.sh("helm lint ./helm/$chartName")
    }
  }

  static def runGitHubSuperLinter(ctx, disableErrors) {
    ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.GitHubSuperLinter.Context, description: GitHubStatus.GitHubSuperLinter.Description) {
      ctx.sh("\$(pwd)/scripts/run-github-super-linter -e DISABLE_ERRORS=$disableErrors -v /etc/ssl/certs/:/etc/ssl/certs/ -v /usr/local/share/ca-certificates/:/usr/local/share/ca-certificates/")
    }
  }

  static def createJUnitReport(ctx) {
    ctx.junit('test-output/junit.xml')
  }

  static def deleteOutput(ctx, containerImage, containerWorkDir) {
    // clean up files created by node/ubuntu user that cannot be deleted by jenkins. Note: uses global environment variable
    ctx.sh("[ -d \"$ctx.WORKSPACE/test-output\" ] && docker run --rm -u node --mount type=bind,source='$ctx.WORKSPACE/test-output',target=/$containerWorkDir/test-output $containerImage rm -rf test-output/*")
  }

  static def analyseNodeJsCode(ctx, sonarQubeEnv, sonarScanner, params) {
    ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.CodeAnalysis.Context, description: GitHubStatus.CodeAnalysis.Description) {
      def scannerHome = ctx.tool sonarScanner
      ctx.withSonarQubeEnv(sonarQubeEnv) {
        def args = ''
        params.each { param ->
          args = args + " -D$param.key=$param.value"
        }

        ctx.sh("$scannerHome/bin/sonar-scanner$args")
      }
    }
  }

  static def analyseDotNetCode(ctx, params) {
    ctx.withCredentials([
      ctx.string(credentialsId: 'sonarcloud-token', variable: 'token'),
    ]) {
      def args = ''
      params.each { param ->
        args = args + " -e $param.key=$param.value"
      }
      ctx.sh("docker run -v \$(pwd)/:/home/dotnet/project -e SONAR_TOKEN=$ctx.token $args defradigital/ffc-dotnet-core-sonar:latest")
    }
  }

  static def buildCodeAnalysisNodeJsParams(projectName, branch, pr) {
    def params = [
    'sonar.organization': 'defra',
    'sonar.projectKey': projectName,
    'sonar.sources': '.'
    ];

    if(pr != '') {
      params = params + buildCodeAnalysisNodeJsPRParams(projectName, branch, pr)
    }

    return params
  }

  static def buildCodeAnalysisNodeJsPRParams(projectName, branch, pr) {
    return [
    'sonar.pullrequest.base': 'master',
    'sonar.pullrequest.branch': branch,
    'sonar.pullrequest.key': pr,
    'sonar.pullrequest.provider': 'GitHub',
    'sonar.pullrequest.github.repository': "defra/${projectName}"
    ];
  }

  static def buildCodeAnalysisDotNetParams(projectName, branch, pr) {
    def params = [
    'SONAR_ORGANIZATION': 'defra',
    'SONAR_PROJECT_KEY': projectName
    ];

    if(pr != '') {
      params = params + buildCodeAnalysisDotNetPRParams(projectName, branch, pr)
    }

    return params
  }

  static def buildCodeAnalysisDotNetPRParams(projectName, branch, pr) {
    return [
    'SONAR_PR_BASE': 'master',
    'SONAR_PR_BRANCH': branch,
    'SONAR_PR_KEY': pr,
    'SONAR_PR_PROVIDER': 'GitHub',
    'SONAR_PR_REPOSITORY': "defra/${projectName}"
    ];
  }

  static def runAcceptanceTests(ctx, pr,  environment, repoName) {
    if (ctx.fileExists('./test/acceptance/docker-compose.yaml')) {
      ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.RunAcceptanceTests.Context, description: GitHubStatus.RunAcceptanceTests.Description) {
        try {
          ctx.dir('./test/acceptance') {
          ctx.sh('mkdir -p -m 777 html-reports')
          def searchKeys = [
            'ingress.endpoint',
            'ingress.server'
          ]
          def appConfigPrefix = environment + '/'
          def endpointConfig =  Utils.getConfigValues(ctx, searchKeys, appConfigPrefix, repoName, false)
          def serverConfig = Utils.getConfigValues(ctx, searchKeys,  appConfigPrefix, Utils.defaultNullLabel, false)
          def endpoint = endpointConfig['ingress.endpoint'].trim()
          def domain = serverConfig['ingress.server'].trim()
          def hostname = pr == '' ? endpoint : "${endpoint}-pr${pr}"
          ctx.withEnv(["TEST_ENVIRONMENT_ROOT_URL=https://${hostname}.${domain}"]) {
          ctx.sh('docker-compose run wdio-cucumber')
          }          
        }
    } finally {
          ctx.sh('docker-compose down -v')
        }         
      }
    } else {
      ctx.echo('No "/test/acceptance/docker-compose.yaml" found therefore skipping this step.')
    }
  }
}
