void call(Map config=[:]) {
  String defaultBranch = 'main'
  String containerSrcFolder = '\\/home\\/node'
  String nodeDevelopmentImage = 'defradigital/node-development'
  String localSrcFolder = '.'
  String lcovFile = './test-output/lcov.info'
  String repoName = ''
  String pr = ''
  String tag = ''
  String mergedPrNo = ''

  node {
    try {
      stage('Set default branch') {
        defaultBranch = build.getDefaultBranch(defaultBranch, config.defaultBranch)
      }

      stage('Checkout source code') {
        build.checkoutSourceCode(defaultBranch)
      }

      stage('Set PR, and tag variables') {
        def version = version.getPackageJsonVersion()
        (repoName, pr, tag, mergedPrNo) = build.getVariables(version, defaultBranch)
      }

      // if (pr != '') {
      //   stage('Verify version incremented') {
      //     version.verifyPackageJsonIncremented(defaultBranch)
      //   }
      // }

      // if (config.containsKey('validateClosure')) {
      //   config['validateClosure']()
      // }

      // stage('Helm lint') {
      //   test.lintHelm(repoName)
      // }

      // stage('npm audit') {
      //   build.npmAudit(config.npmAuditLevel, config.npmAuditLogType, config.npmAuditFailOnIssues, nodeDevelopmentImage, containerSrcFolder, pr)
      // }

      // stage('Snyk test') {
      //   build.snykTest(config.snykFailOnIssues, config.snykOrganisation, config.snykSeverity, pr)
      // }

      // stage('Provision resources') {
      //   provision.createResources(config.environment, repoName, pr)
      // }

      // if (config.containsKey('buildClosure')) {
      //   config['buildClosure']()
      // }

      if (fileExists('./docker-compose.test.yaml')) {
        stage('Build test image') {
          build.buildTestImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, BUILD_NUMBER, tag)
        }

        stage('Run tests') {
          build.runTests(repoName, repoName, BUILD_NUMBER, tag, pr, config.environment)
        }

        stage('Create JUnit report') {
          test.createJUnitReport()
        }

        stage('Fix lcov report') {
          utils.replaceInFile(containerSrcFolder, localSrcFolder, lcovFile)
        }

        stage('Publish pact broker') {
          pact.publishContractsToPactBroker(repoName, version.getPackageJsonVersion(), utils.getCommitSha())
        }
      }

      // stage('SonarCloud analysis') {
      //   test.analyseNodeJsCode(SONARCLOUD_ENV, SONAR_SCANNER, repoName, BRANCH_NAME, defaultBranch, pr)
      // }

      // stage('Run Zap Scan') {
      //   test.runZapScan(repoName, BUILD_NUMBER, tag)
      // }

      // stage('Run Accessibility tests') {
      //   test.runPa11y(repoName, BUILD_NUMBER, tag)
      // }

      // if (config.containsKey('testClosure')) {
      //   config['testClosure']()
      // }

      // stage('Build & push container image') {
      //   build.buildAndPushContainerImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, tag)
      // }

      // if (pr != '') {
      //   stage('Helm install') {
      //     helm.deployChart(config.environment, DOCKER_REGISTRY, repoName, tag, pr)
      //   }
      // } else {
      //   stage('Publish chart') {
      //     helm.publishChart(DOCKER_REGISTRY, repoName, tag, HELM_CHART_REPO_TYPE)
      //   }

      //   stage('Trigger GitHub release') {
      //     withCredentials([
      //       string(credentialsId: 'github-auth-token', variable: 'gitToken')
      //     ]) {
      //       String commitMessage = utils.getCommitMessage()
      //       release.trigger(tag, repoName, commitMessage, gitToken)
      //     }
      //   }

      //   stage('Trigger Deployment') {
      //     withCredentials([
      //       string(credentialsId: "$repoName-deploy-token", variable: 'jenkinsToken')
      //     ]) {
      //       deploy.trigger(JENKINS_DEPLOY_SITE_ROOT, repoName, jenkinsToken, ['chartVersion': tag, 'environment': config.environment, 'helmChartRepoType': HELM_CHART_REPO_TYPE])
      //     }
      //   }
      // }

      // if (config.containsKey('deployClosure')) {
      //   config['deployClosure']()
      // }

      stage('Run Acceptance Tests') {
        test.runAcceptanceTests(pr, config.environment, repoName)
      }

    } catch(e) {
      def errMsg = utils.getErrorMessage(e)
      echo("Build failed with message: $errMsg")

      stage('Send build failure slack notification') {
        notifySlack.buildFailure('#generalbuildfailures', defaultBranch)
      }

      if (config.containsKey('failureClosure')) {
        config['failureClosure']()
      }

      throw e
    } finally {
      stage('Clean up test output') {
        test.changeOwnershipOfWorkspace(nodeDevelopmentImage, containerSrcFolder)
      }

      stage('Clean up resources') {
        provision.deleteBuildResources(repoName, pr)
      }

      if (config.containsKey('finallyClosure')) {
        config['finallyClosure']()
      }
    }
  }
}
