void call(Map config=[:]) {
  String defaultBranch = 'main'
  String environment = 'snd2'
  String tag = ''
  String mergedPrNo = ''
  String pr = ''
  String repoName = ''
  String csProjVersion = ''
  String containerSrcFolder = '\\/home\\/dotnet'
  String dotnetDevelopmentImage = 'defradigital/dotnetcore-development'
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
        build.checkoutSourceCode(defaultBranch)
      }

      if (fileExists('./helm/')) {
        hasHelmChart = true
      }

      stage('Set PR and tag variables') {
        csProjVersion = version.getCSProjVersion(config.project)
        (repoName, pr, tag, mergedPrNo) = build.getVariables(csProjVersion, defaultBranch)
      }

      if (pr != '') {
        stage('Verify version incremented') {
          version.verifyCSProjIncremented(config.project, defaultBranch)
        }
      }

      if (config.containsKey('validateClosure')) {
        config['validateClosure']()
      }

      if (hasHelmChart) {
        stage('Helm lint') {
          test.lintHelm(repoName)
        }
      }

      if (config.containsKey('buildClosure')) {
        config['buildClosure']()
      }

      if (fileExists('./docker-compose.snyk.yaml')) {
        stage('Snyk test') {
          // ensure obj folder exists and is writable by all
          sh("chmod 777 ${config.project}/obj || mkdir -p -m 777 ${config.project}/obj")
          build.extractSynkFiles(repoName, BUILD_NUMBER, tag)
          build.snykTest(config.snykFailOnIssues, config.snykOrganisation, config.snykSeverity, "${config.project}.sln", pr)
        }
      }

      stage('Provision any required resources') {
        provision.createResources(environment, repoName, tag, pr)
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

        if (pr == '') {
          stage('Publish pact broker') {
              pact.publishContractsToPactBroker(repoName, csProjVersion, utils.getCommitSha())
          }
        }
      }

      stage('SonarCloud analysis') {
        test.analyseDotNetCode(repoName, config.project, BRANCH_NAME, defaultBranch, pr)
      }

      if (config.containsKey('testClosure')) {
        config['testClosure']()
      }

      stage('Push container image') {
        build.buildAndPushContainerImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, tag)
      }

      if (hasHelmChart) {
        if (pr != '') {
          stage('Helm install') {
            helm.deployChart(environment, DOCKER_REGISTRY, repoName, tag, pr)
          }
        }
        else {
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

      if (triggerDeployment && hasHelmChart && pr == '') {
        // Deploy start

          stage('Trigger ADO pipelines') {
            namespace = helm.getNamespace(repoName)
            ado.triggerNewFFCPipeline(namespace, repoName, tag)
          }

      // Deploy End
      }

      if (config.containsKey('deployClosure')) {
        config['deployClosure']()
      }
    } catch(e) {
      echo("Build failed with message: $e.message")

      stage('Send build failure slack notification') {
        notifySlack.buildFailure('generalbuildfailures', defaultBranch)
      }

      if (config.containsKey('failureClosure')) {
        config['failureClosure']()
      }

      throw e
    } finally {
      stage('Change ownership of outputs') {
        test.changeOwnershipOfWorkspace(dotnetDevelopmentImage, containerSrcFolder)
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
