package uk.gov.defra.ffc

import uk.gov.defra.ffc.GitHubStatus
import uk.gov.defra.ffc.Utils
import uk.gov.defra.ffc.Version

class Tests implements Serializable {

  static void runTests(ctx, projectName, serviceName, buildNumber, tag, pr, environment) {
    ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.RunTests.Context, description: GitHubStatus.RunTests.Description) {
      String sanitizedTag = Utils.sanitizeTag(tag)
      try {
        ctx.sh('mkdir -p -m 777 test-output')
        if (ctx.fileExists('./docker-compose.migrate.yaml')) {
          ctx.sh("docker-compose -p $projectName-${sanitizedTag}-$buildNumber -f docker-compose.migrate.yaml run database-up")
        }
        ctx.withEnv(Provision.getBuildQueueEnvVars(ctx, serviceName, pr)) {
          ctx.sh("docker-compose -p $projectName-${sanitizedTag}-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml run $serviceName")
        }
      } finally {
        ctx.sh("docker-compose -p $projectName-${sanitizedTag}-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml down -v")
        if (ctx.fileExists('./docker-compose.migrate.yaml')) {
          ctx.sh("docker-compose -p $projectName-${sanitizedTag}-$buildNumber -f docker-compose.migrate.yaml down -v")
        }
      }
    }
  }

  static void runServiceAcceptanceTests(ctx, projectName, serviceName, buildNumber, tag, pr) {
    ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.RunTests.Context, description: GitHubStatus.RunTests.Description) {
      String sanitizedTag = Utils.sanitizeTag(tag)
      String acceptanceTestService = 'test-runner'
      try {
        ctx.sh('mkdir -p -m 777 test-output')
        if (ctx.fileExists('./docker-compose.migrate.yaml')) {
          ctx.sh("docker-compose -p $projectName-${sanitizedTag}-$buildNumber -f docker-compose.migrate.yaml run database-up")
        }
        ctx.withEnv(Provision.getBuildQueueEnvVars(ctx, serviceName, pr)) {
          ctx.sh("docker-compose -p $projectName-${sanitizedTag}-$buildNumber -f docker-compose.yaml -f docker-compose.acceptance.yaml run $serviceName-$acceptanceTestService")
        }
      } finally {
        ctx.sh("docker-compose -p $projectName-${sanitizedTag}-$buildNumber -f docker-compose.yaml -f docker-compose.acceptance.yaml down -v")
        if (ctx.fileExists('./docker-compose.migrate.yaml')) {
          ctx.sh("docker-compose -p $projectName-${sanitizedTag}-$buildNumber -f docker-compose.migrate.yaml down -v")
          generateHtmlReport(ctx, projectName)
        }
      }
    }
  }

  static void generateHtmlReport(ctx, projectName) {
    ctx.publishHTML(target: [
              allowMissing: false,
              alwaysLinkToLastBuild: false,
              keepAll: true,
              reportDir: 'test-output',
              reportFiles: 'cucumber-report.html',
              reportName: 'Service Acceptance Test Report',
              reportTitles: "$projectName - Service Acceptance Test Report"
            ])
  }

  static void runZapScan(ctx, projectName, buildNumber, tag) {
    def zapDockerComposeFile = 'docker-compose.zap.yaml'
    ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.ZapScan.Context, description: GitHubStatus.ZapScan.Description) {
      String sanitizedTag = Utils.sanitizeTag(tag)
      try {
        // test-output exists if stage is run after 'runTests', take no risks and create it
        ctx.sh('mkdir -p -m 666 test-output')
        ctx.sh("docker-compose -p $projectName-${sanitizedTag}-$buildNumber -f docker-compose.yaml -f $zapDockerComposeFile run -v /etc/ssl/certs/:/etc/ssl/certs/ -v /usr/local/share/ca-certificates/:/usr/local/share/ca-certificates/ zap-baseline-scan")
        } finally {
        ctx.sh("docker-compose -p $projectName-${sanitizedTag}-$buildNumber -f docker-compose.yaml -f $zapDockerComposeFile down -v")
      }
    }
  }

  static void runAccessibilityTests(ctx, projectName, buildNumber, tag, accessibilityTestType) {
    def dockerComposeFile = "docker-compose.${accessibilityTestType}.yaml"
    ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.RunAccessibilityTests.Contexts[accessibilityTestType], description: GitHubStatus.RunAccessibilityTests.Description) {
      String sanitizedTag = Utils.sanitizeTag(tag)
      try {
        ctx.sh('mkdir -p -m 666 test-output')
        ctx.sh("docker-compose -p $projectName-${sanitizedTag}-$buildNumber -f docker-compose.yaml -f $dockerComposeFile run -v /etc/ssl/certs/:/etc/ssl/certs/ -v /usr/local/share/ca-certificates/:/usr/local/share/ca-certificates/ $accessibilityTestType")
        } finally {
        ctx.sh("docker-compose -p $projectName-${sanitizedTag}-$buildNumber -f docker-compose.yaml -f $dockerComposeFile down -v")
      }
    }
  }

  static void lintHelm(ctx, chartName) {
    ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.HelmLint.Context, description: GitHubStatus.HelmLint.Description) {
      Helm.addHelmRepo(ctx, 'ffc-public', ctx.HELM_CHART_REPO_PUBLIC)
      ctx.sh("helm dependency update ./helm/$chartName")
      ctx.sh("helm lint ./helm/$chartName")
    }
  }

  static void runGitHubSuperLinter(ctx, disableErrors) {
    ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.GitHubSuperLinter.Context, description: GitHubStatus.GitHubSuperLinter.Description) {
      ctx.sh("\$(pwd)/scripts/run-github-super-linter -e DISABLE_ERRORS=$disableErrors -v /etc/ssl/certs/:/etc/ssl/certs/ -v /usr/local/share/ca-certificates/:/usr/local/share/ca-certificates/")
    }
  }

  static void createJUnitReport(ctx) {
    ctx.junit('test-output/junit.xml')
  }

  static void changeOwnershipOfWorkspace(ctx, containerImage, containerWorkDir) {
    ctx.sh("[ -d \"$ctx.WORKSPACE\" ]  && docker run --rm -u root --privileged --mount type=bind,source='$ctx.WORKSPACE',target=/$containerWorkDir $containerImage chown $ctx.JENKINS_USER_ID:$ctx.JENKINS_GROUP_ID -R .")
  }

  static void analyseNodeJsCode(ctx, sonarQubeEnv, sonarScanner, params) {
    ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.CodeAnalysis.Context, description: GitHubStatus.CodeAnalysis.Description) {
      ctx.withCredentials([
        ctx.string(credentialsId: 'sonarcloud-token', variable: 'token'),
      ]) {
        def args = ''
        params.each { param ->
          args = args + " -e $param.key=$param.value"
        }
        ctx.sh("docker run -v \$(pwd)/:/home/node/project -e SONAR_TOKEN=$ctx.token $args defradigital/ffc-node-sonar:1.0.2-node20")
      }
    }
  }

  static void analyseDotNetCode(ctx, projectName, params) {
    ctx.withCredentials([
      ctx.string(credentialsId: 'sonarcloud-token', variable: 'token'),
    ]) {
      def args = ''
      params.each { param ->
        args = args + " -e $param.key=$param.value"
      }
      String imageTag = getDotNetSonarImageVersion(ctx, projectName)
      ctx.sh("docker run -v \$(pwd)/:/home/dotnet/project -e SONAR_TOKEN=$ctx.token $args defradigital/ffc-dotnet-core-sonar:$imageTag")
    }
  }

  static String getDotNetSonarImageVersion(ctx, projectName) {
    String targetFramework = Version.getCSTargetFramework(ctx, projectName)
    switch (targetFramework) {
      case "netcoreapp3.1":
        return "1.2.3-dotnet3.1"
      case "net6.0":
        return "1.7.0-dotnet6.0"
      case "net8.0"
        return "1.7.0-dotnet8.0"
    }
  }

  static def buildCodeAnalysisParams(projectName, branch, defaultBranch, pr) {
    def params = [
    'SONAR_ORGANIZATION': 'defra',
    'SONAR_PROJECT_KEY': projectName
    ];

    if (pr != '') {
      params = params + buildCodeAnalysisPRParams(projectName, branch, defaultBranch, pr)
    }

    return params
  }

  static def buildCodeAnalysisPRParams(projectName, branch, defaultBranch, pr) {
    return [
      'SONAR_PR_BASE': defaultBranch,
      'SONAR_PR_BRANCH': branch,
      'SONAR_PR_KEY': pr,
      'SONAR_PR_PROVIDER': 'GitHub',
      'SONAR_PR_REPOSITORY': "defra/${projectName}"
    ];
  }

  static String buildUrl(ctx, pr,  environment, repoName) {
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

  static void runJmeterTests(ctx, pr,  environment, repoName) {
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

  static void runAcceptanceTests(ctx, pr,  environment, repoName) {
    ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.RunAcceptanceTests.Context, description: GitHubStatus.RunAcceptanceTests.Description) {
      try {
        ctx.withCredentials([
            ctx.usernamePassword(credentialsId: 'browserstack-credentials', usernameVariable: 'browserStackUsername', passwordVariable: 'browserStackAccessToken')
          ]) {
          def envVars = Provision.getPrQueueEnvVars(ctx, repoName, pr)
          envVars.push("BROWSERSTACK_USERNAME=${ctx.browserStackUsername}")
          envVars.push("BROWSERSTACK_ACCESS_KEY=${ctx.browserStackAccessToken}")
          def url = buildUrl(ctx, pr, environment, repoName)
          envVars.push("TEST_ENVIRONMENT_ROOT_URL=https://${url}")

          ctx.dir('./test/acceptance') {
            ctx.sh('mkdir -p -m 777 html-reports')

            ctx.withEnv(envVars) {
              // Intentionally only use `docker-compose.yaml`. Abort on
              // container exit ensures exit code is returned from `sh` step.
              ctx.sh('docker-compose -f docker-compose.yaml up --build --abort-on-container-exit')
            }
          }
        }
      } finally {
        ctx.sh('docker-compose down -v')
      }
    }
  }

}
