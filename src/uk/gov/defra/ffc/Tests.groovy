package uk.gov.defra.ffc

import uk.gov.defra.ffc.GitHubStatus
import uk.gov.defra.ffc.Utils

class Tests implements Serializable {
  static def runTests(ctx, projectName, serviceName, buildNumber, tag, pr, environment) {
    ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.RunTests.Context, description: GitHubStatus.RunTests.Description) {
      try {
        ctx.sh('mkdir -p -m 777 test-output')
        if (ctx.fileExists('./docker-compose.migrate.yaml')) {
          ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.migrate.yaml run database-up")
        }
        ctx.withEnv(Provision.getBuildQueueEnvVars(ctx, serviceName, pr)) {
          ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml run $serviceName")
        }
      } finally {
        ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml down -v")
        if (ctx.fileExists('./docker-compose.migrate.yaml')) {
          ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.migrate.yaml down -v")
        }
      }
    }
  }

  static def runZapScan(ctx, projectName, buildNumber, tag) {
    def zapDockerComposeFile = 'docker-compose.zap.yaml'
      ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.ZapScan.Context, description: GitHubStatus.ZapScan.Description) {
        try {
          // test-output exists if stage is run after 'runTests', take no risks and create it
          ctx.sh('mkdir -p -m 666 test-output')
          ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.yaml -f $zapDockerComposeFile run -v /etc/ssl/certs/:/etc/ssl/certs/ -v /usr/local/share/ca-certificates/:/usr/local/share/ca-certificates/ zap-baseline-scan")
        } finally {
          ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.yaml -f $zapDockerComposeFile down -v")
        }
      }
  }

  static def runAccessibilityTests(ctx, projectName, buildNumber, tag, accessibilityTestType) {
    def dockerComposeFile = "docker-compose.${accessibilityTestType}.yaml"
      ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.RunAccessibilityTests.Contexts[accessibilityTestType], description: GitHubStatus.RunAccessibilityTests.Description) {
        try {
          ctx.sh('mkdir -p -m 666 test-output')
          ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.yaml -f $dockerComposeFile run -v /etc/ssl/certs/:/etc/ssl/certs/ -v /usr/local/share/ca-certificates/:/usr/local/share/ca-certificates/ $accessibilityTestType")
        } finally {
          ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.yaml -f $dockerComposeFile down -v")
        }
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

  static def changeOwnershipOfWorkspace(ctx, containerImage, containerWorkDir) {
    ctx.sh("[ -d \"$ctx.WORKSPACE\" ]  && docker run --rm -u root --privileged --mount type=bind,source='$ctx.WORKSPACE',target=/$containerWorkDir $containerImage chown $ctx.JENKINS_USER_ID:$ctx.JENKINS_GROUP_ID -R .")
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

  static def buildCodeAnalysisNodeJsParams(projectName, branch, defaultBranch, pr) {
    def params = [
    'sonar.organization': 'defra',
    'sonar.projectKey': projectName,
    'sonar.sources': '.'
    ];

    if(pr != '') {
      params = params + buildCodeAnalysisNodeJsPRParams(projectName, branch, defaultBranch, pr)
    }

    return params
  }

  static def buildCodeAnalysisNodeJsPRParams(projectName, branch, defaultBranch, pr) {
    return [
    'sonar.pullrequest.base': defaultBranch,
    'sonar.pullrequest.branch': branch,
    'sonar.pullrequest.key': pr,
    'sonar.pullrequest.provider': 'GitHub',
    'sonar.pullrequest.github.repository': "defra/${projectName}"
    ];
  }

  static def buildCodeAnalysisDotNetParams(projectName, branch, defaultBranch, pr) {
    def params = [
    'SONAR_ORGANIZATION': 'defra',
    'SONAR_PROJECT_KEY': projectName
    ];

    if(pr != '') {
      params = params + buildCodeAnalysisDotNetPRParams(projectName, branch, defaultBranch, pr)
    }

    return params
  }

  static def buildCodeAnalysisDotNetPRParams(projectName, branch, defaultBranch, pr) {
    return [
    'SONAR_PR_BASE': defaultBranch,
    'SONAR_PR_BRANCH': branch,
    'SONAR_PR_KEY': pr,
    'SONAR_PR_PROVIDER': 'GitHub',
    'SONAR_PR_REPOSITORY': "defra/${projectName}"
    ];
  }

  static def buildUrl(ctx, pr,  environment, repoName) {
    def searchKeys = [
            'ingress.endpoint',
            'ingress.server'
          ]
    def appConfigPrefix = environment + '/'
    def endpointConfig =  Utils.getConfigValues(ctx, searchKeys, appConfigPrefix, repoName, false)
    def serverConfig = Utils.getConfigValues(ctx, searchKeys,  appConfigPrefix, Utils.defaultNullLabel, false)
    def endpoint = endpointConfig['ingress.endpoint'].trim()
    def domain = serverConfig['ingress.server'].trim()
    def hostname = pr == '' ? endpoint : "${repoName}-pr${pr}"

    return "${hostname}.${domain}"
  }

  static def runJmeterTests(ctx, pr,  environment, repoName) {
      ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.RunPerformanceTests.Context, description: GitHubStatus.RunPerformanceTests.Description) {
        try {
          ctx.dir('./test/performance') {
          ctx.sh('mkdir -p -m 777 html-reports')

          def url = buildUrl(ctx, pr,  environment, repoName)

          def dynamicJmeterContent = "https;${url};443"

          ctx.writeFile(file: "jmeterConfig.csv", text: dynamicJmeterContent, encoding: "UTF-8")

          ctx.sh('docker-compose -f ../../docker-compose.yaml -f docker-compose.jmeter.yaml run jmeter-test')

        }
      } finally {
        ctx.sh('docker-compose down -v')
      }
    }
  }

  static def runAcceptanceTests(ctx, pr,  environment, repoName) {
      ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.RunAcceptanceTests.Context, description: GitHubStatus.RunAcceptanceTests.Description) {
        try {
          ctx.withCredentials([
            ctx.usernamePassword(credentialsId: 'browserstack-credentials', usernameVariable: 'browserStackUsername', passwordVariable: 'browserStackAccessToken')
          ]) {
            // TODO: env vars for queues are those used by the tests rather than those used by the application
            // NOTE: Get queue env vars used by app
            // def configDict = Provision.getProvisionedQueueConfigValues(ctx, repoName, pr)
            // ctx.echo("CONFIG VALUES (in dict): $configDict")

            // def configArray = []
            // configDict.each {
            //   configArray.push("")
            // }
            // def values = configDict.values()
            // ctx.echo("CONFIG VALUES (in array): $values")

            def envVars = Provision.getPrQueueEnvVars(ctx, repoName, pr)
            ctx.echo("MESSAGE QUEUE CREDS: $envVars")

            // def envVars = Provision.getBuildQueueEnvVars(ctx, repoName, pr, environment)
            // def envVars = configDict.values() + messageQueueCreds
            envVars.push("BROWSERSTACK_USERNAME=${ctx.browserStackUsername}")
            envVars.push("BROWSERSTACK_ACCESS_KEY=${ctx.browserStackAccessToken}")
            def url = buildUrl(ctx, pr, environment, repoName)
            envVars.push("TEST_ENVIRONMENT_ROOT_URL=https://${url}")
            ctx.echo("$envVars")

            ctx.dir('./test/acceptance') {
            ctx.sh('mkdir -p -m 777 html-reports')

            ctx.withEnv(envVars) {
              // ctx.sh('docker-compose -f docker-compose.yaml build')
              // ctx.sh('docker-compose run wdio-cucumber')
              ctx.sh('docker-compose up --build')
            }
          }
        }
    } finally {
          ctx.sh('docker-compose down -v')
        }
      }
  }
}
