def call(Map config=[:]) {
  def containerSrcFolder = '\\/home\\/node'
  def nodeDevelopmentImage = 'defradigital/node-development'
  def localSrcFolder = '.'
  def lcovFile = './test-output/lcov.info'
  def repoName = ''
  def pr = ''
  def tag = ''
  def mergedPrNo = ''

  node {
    try {
      stage('Checkout source code') {
        build.checkoutSourceCode()
      }

      stage('Set PR, and tag variables') {
        (repoName, pr, tag, mergedPrNo) = build.getVariables(version.getPackageJsonVersion())
      }

      if (pr != '') {
        stage('Verify version incremented') {
          version.verifyPackageJsonIncremented()
        }
      }

      if (config.containsKey('validateClosure')) {
        config['validateClosure']()
      }

      stage('Helm lint') {
        test.lintHelm(repoName)
      }

      stage('npm audit') {
        build.npmAudit(config.npmAuditLevel, config.npmAuditLogType, config.npmAuditFailOnIssues, nodeDevelopmentImage, containerSrcFolder, pr)
      }

      stage('Snyk test') {
        build.snykTest(config.snykFailOnIssues, config.snykOrganisation, config.snykSeverity, pr)
      }
      
      if (fileExists('./docker-compose.test.yaml')) {
        stage('Build test image') {
        build.buildTestImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, BUILD_NUMBER, tag)
        }
      } else {
      echo("docker-compose.test.yaml not found, skipping test run")
      }
      

      stage('Provision resources') {
        provision.createResources(config.environment, repoName, pr)
      }

      if (config.containsKey('buildClosure')) {
        config['buildClosure']()
      }

      if (fileExists('./docker-compose.test.yaml')) {
        stage('Run tests') {
        build.runTests(repoName, repoName, BUILD_NUMBER, tag, pr, config.environment)
      }
      } else {
      echo("docker-compose.test.yaml not found, skipping test run")
      }
      
      if (fileExists('./docker-compose.test.yaml')) {
        stage('Create JUnit report') {
        test.createJUnitReport()
        }
      } else {
      echo("docker-compose.test.yaml not found, skipping test run")
      }
      
      if (fileExists('./docker-compose.test.yaml')) {
        stage('Fix lcov report') {
        utils.replaceInFile(containerSrcFolder, localSrcFolder, lcovFile)
        }
      } else {
      echo("docker-compose.test.yaml not found, skipping test run")
      }
      

      stage('SonarCloud analysis') {
        test.analyseNodeJsCode(SONARCLOUD_ENV, SONAR_SCANNER, repoName, BRANCH_NAME, pr)
      }

      stage('Run Zap Scan') {
        test.runZapScan(repoName, BUILD_NUMBER, tag)
      }

      stage('Publish pact broker') {
        pact.publishContractsToPactBroker(repoName, version.getPackageJsonVersion(), utils.getCommitSha())
      }

      if (config.containsKey('testClosure')) {
        config['testClosure']()
      }

      stage('Build & push container image') {
        build.buildAndPushContainerImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, tag)
      }

      if (pr != '') {
        stage('Helm install') {
          helm.deployChart(config.environment, DOCKER_REGISTRY, repoName, tag, pr)
        }
      } else {
        stage('Publish chart') {
          helm.publishChart(DOCKER_REGISTRY, repoName, tag, HELM_CHART_REPO_TYPE)
        }

        stage('Trigger GitHub release') {
          withCredentials([
            string(credentialsId: 'github-auth-token', variable: 'gitToken')
          ]) {
            release.trigger(tag, repoName, tag, gitToken)
          }
        }

        stage('Trigger Deployment') {
          withCredentials([
            string(credentialsId: "$repoName-deploy-token", variable: 'jenkinsToken')
          ]) {
            deploy.trigger(JENKINS_DEPLOY_SITE_ROOT, repoName, jenkinsToken, ['chartVersion': tag, 'environment': config.environment, 'helmChartRepoType': HELM_CHART_REPO_TYPE])
          }
        }
      }

      if (config.containsKey('deployClosure')) {
        config['deployClosure']()
      }

      stage('Run Acceptance Tests') {
        test.runAcceptanceTests(pr, config.environment, repoName)
      }

    } catch(e) {
      def errMsg = utils.getErrorMessage(e)
      echo("Build failed with message: $errMsg")

      stage('Send build failure slack notification') {
        notifySlack.buildFailure('#generalbuildfailures')
      }

      if (config.containsKey('failureClosure')) {
        config['failureClosure']()
      }

      throw e
    } finally {
      stage('Clean up test output') {
        test.deleteOutput(nodeDevelopmentImage, containerSrcFolder)
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
