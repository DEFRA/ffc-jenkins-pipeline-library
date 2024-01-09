void call(Map config=[:]) {
  String defaultBranch = 'main'
  String environment = 'snd'
  String containerSrcFolder = '\\/home\\/node'
  String nodeDevelopmentImage = 'defradigital/node-development'
  String localSrcFolder = '.'
  String lcovFile = './test-output/lcov.info'
  String repoName = ''
  String pr = ''
  String tag = ''
  String mergedPrNo = ''
  Boolean hasHelmChart = false
  Boolean triggerDeployment = config.triggerDeployment != null ? config.triggerDeployment : true
  String deploymentPipelineName = ''

  node {
    try {
      stage('Ensure clean workspace') {
        deleteDir()
      }

      stage('Set default branch') {
        defaultBranch = build.getDefaultBranch(defaultBranch, config.defaultBranch)
      }

      stage('Set environment') {
        environment = config.environment != null ? config.environment : environment
      }

      stage('Checkout source code') {
        // build.checkoutSourceCode(defaultBranch)
      }

      if (fileExists('./helm/')) {
        hasHelmChart = true
      }

      stage('Set PR and tag variables') {
        def version = version.getPackageJsonVersion()
        (repoName, pr, tag, mergedPrNo) = build.getVariables(version, defaultBranch)
      }

      if (pr != '') {
        stage('Verify version incremented') {
          version.verifyPackageJsonIncremented(defaultBranch)
        }
      } else {
        stage('Rebuild all feature branches') {
          build.triggerMultiBranchBuilds(defaultBranch)
        }
      }

      if (config.containsKey('validateClosure')) {
        config['validateClosure']()
      }

      if(hasHelmChart) {
        stage('Helm lint') {
          test.lintHelm(repoName)
        }
      }

      stage('npm audit') {
        build.npmAudit(config.npmAuditLevel, config.npmAuditLogType, config.npmAuditFailOnIssues, nodeDevelopmentImage, containerSrcFolder, pr)
      }

      stage('Snyk test') {
        build.snykTest(config.snykFailOnIssues, config.snykOrganisation, config.snykSeverity, pr)
      }

      stage('Provision any required resources') {
        provision.createResources(environment, repoName, pr)
      }

      if (config.containsKey('buildClosure')) {
        config['buildClosure']()
      }

      if (fileExists('./docker-compose.test.yaml')) {
        stage('Build test image') {
          build.buildTestImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, BUILD_NUMBER, tag)
        }

        stage('Run tests') {
          build.runTests(repoName, repoName, BUILD_NUMBER, tag, pr, environment)
        }

        if (fileExists('./docker-compose.acceptance.yaml')) {
        stage('Run Service Acceptance Tests') {
          test.runServiceAcceptanceTests(repoName, repoName, BUILD_NUMBER, tag, pr)
        }
      }

        stage('Create JUnit report') {
          test.createJUnitReport()
        }

        stage('Fix lcov report') {
          utils.replaceInFile(containerSrcFolder, localSrcFolder, lcovFile)
        }

        if (pr == '') {
          stage('Publish pact broker') {
            pact.publishContractsToPactBroker(repoName, version.getPackageJsonVersion(), utils.getCommitSha())
          }
        }
      }

      stage('SonarCloud analysis') {
        test.analyseNodeJsCode(SONARCLOUD_ENV, SONAR_SCANNER, repoName, BRANCH_NAME, defaultBranch, pr)
      }

      if (fileExists('./docker-compose.zap.yaml')) {
        stage('Run Zap Scan') {
          test.runZapScan(repoName, BUILD_NUMBER, tag)
        }
      }

      if (fileExists('./docker-compose.pa11y.yaml')) {
        stage('Run pa11y accessibility tests') {
          test.runAccessibilityTests(repoName, BUILD_NUMBER, tag, 'pa11y')
        }
      }

      if (fileExists('./docker-compose.axe.yaml')) {
        stage('Run AXE accessibility tests') {
          test.runAccessibilityTests(repoName, BUILD_NUMBER, tag, 'axe')
        }
      }

      if (config.containsKey('testClosure')) {
        config['testClosure']()
      }

      stage('Build & push container image') {
        build.buildAndPushContainerImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, tag)
      }

      if(hasHelmChart) {
        if (pr != '') {
          stage('Helm install') {
            helm.deployChart(environment, DOCKER_REGISTRY, repoName, tag, pr)
          }
        } else {
          stage('Publish chart') {
            helm.publishChart(DOCKER_REGISTRY, repoName, tag, HELM_CHART_REPO_TYPE)
          }
        }
      }


      if (pr == '') {
        stage('Trigger GitHub release') {
          withCredentials([
            string(credentialsId: 'github-auth-token', variable: 'gitToken')
          ]) {
            String commitMessage = utils.getCommitMessage()
            release.trigger(tag, repoName, commitMessage, gitToken)
          }
        }
      }

      if(triggerDeployment && hasHelmChart && pr == '') {
        stage('Set deployment pipeline name') {
          deploymentPipelineName = config.deploymentPipelineName != null ? config.deploymentPipelineName : "${repoName}-deploy"
        }

        stage('Trigger Deployment') {
          if (utils.checkCredentialsExist("$repoName-deploy-token")) {
            withCredentials([
              string(credentialsId: "$repoName-deploy-token", variable: 'jenkinsToken')
            ]) {
              deploy.trigger(JENKINS_DEPLOY_SITE_ROOT, deploymentPipelineName, jenkinsToken, ['chartVersion': tag, 'environment': environment, 'helmChartRepoType': HELM_CHART_REPO_TYPE])
            }
          } else {
            withCredentials([
              string(credentialsId: 'default-deploy-token', variable: 'jenkinsToken')
            ]) {
              deploy.trigger(JENKINS_DEPLOY_SITE_ROOT, deploymentPipelineName, jenkinsToken, ['chartVersion': tag, 'environment': environment, 'helmChartRepoType': HELM_CHART_REPO_TYPE])
            }
          }
        }
      }

      if (config.containsKey('deployClosure')) {
        config['deployClosure']()
      }

      if (fileExists('./test/acceptance/docker-compose.yaml') && hasHelmChart) {
        stage('Run Acceptance Tests') {
          test.runAcceptanceTests(pr, environment, repoName)
        }
      }

      if (fileExists('./test/performance/docker-compose.jmeter.yaml') && fileExists('./test/performance/jmeterConfig.csv') && hasHelmChart) {
        stage('Run Jmeter Tests') {
          test.runJmeterTests(pr, environment, repoName)
        }
      }

    } catch(e) {
      def errMsg = utils.getErrorMessage(e)
      echo("Build failed with message: $errMsg")

      stage('Send build failure slack notification') {
        notifySlack.buildFailure('generalbuildfailures', defaultBranch)
      }

      if (config.containsKey('failureClosure')) {
        config['failureClosure']()
      }

      throw e
    } finally {
      stage('Change ownership of outputs') {
        test.changeOwnershipOfWorkspace(nodeDevelopmentImage, containerSrcFolder)
      }

      stage('Clean up resources') {
        provision.deleteBuildResources(repoName, pr)
      }

      if (config.containsKey('finallyClosure')) {
        config['finallyClosure']()
      }

      stage('Publish to Log Analytics') {
        consoleLogs.save('/var/log/jenkins/console')
      }
    }
  }
}
