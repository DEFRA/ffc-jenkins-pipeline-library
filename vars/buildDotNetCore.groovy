void call(Map config=[:]) {
  String defaultBranch = 'main'
  String environment = 'snd'
  String tag = ''
  String mergedPrNo = ''
  String pr = ''
  String repoName = ''
  String csProjVersion = ''
  String containerSrcFolder = '\\/home\\/dotnet'
  String dotnetDevelopmentImage = 'defradigital/dotnetcore-development'
  Boolean hasHelmChart = true

  node {
    try {

      if (!fileExists('./helm/')) {
        hasHelmChart = false
      }

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

      if(hasHelmChart) {
        stage('Helm lint') {
          test.lintHelm(repoName)
        }
      }

      if (config.containsKey('buildClosure')) {
        config['buildClosure']()
      }

      if (fileExists('./docker-compose.snyk.yaml')){
       stage('Snyk test') {
         // ensure obj folder exists and is writable by all
         sh("chmod 777 ${config.project}/obj || mkdir -p -m 777 ${config.project}/obj")
         build.extractSynkFiles(repoName, BUILD_NUMBER, tag)
         build.snykTest(config.snykFailOnIssues, config.snykOrganisation, config.snykSeverity, "${config.project}.sln", pr)
       }
     }

      stage('Provision any required resources') {
        provision.createResources(environment, repoName, pr)
      }

      if (fileExists('./docker-compose.test.yaml')) {
        stage('Build test image') {
          build.buildTestImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, BUILD_NUMBER, tag)
        }

        stage('Run tests') {
          build.runTests(repoName, repoName, BUILD_NUMBER, tag, pr, environment)
        }

        if (pr == '') {
          stage('Publish pact broker') {
              pact.publishContractsToPactBroker(repoName, csProjVersion, utils.getCommitSha())
            }
        }
      }

      stage('SonarCloud analysis') {
        test.analyseDotNetCode(repoName, BRANCH_NAME, defaultBranch, pr)
      }

      if (config.containsKey('testClosure')) {
        config['testClosure']()
      }

      stage('Push container image') {
        build.buildAndPushContainerImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, tag)
      }

      if(hasHelmChart) {
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

      if(hasHelmChart && pr == '') {
        stage('Trigger Deployment') {
          if (utils.checkCredentialsExist("$repoName-deploy-token")) {            
            withCredentials([
              string(credentialsId: "$repoName-deploy-token", variable: 'jenkinsToken')
            ]) {
              deploy.trigger(JENKINS_DEPLOY_SITE_ROOT, repoName, jenkinsToken, ['chartVersion': tag, 'environment': environment, 'helmChartRepoType': HELM_CHART_REPO_TYPE])
            }
          } else {            
            withCredentials([
              string(credentialsId: 'default-deploy-token', variable: 'jenkinsToken')
            ]) {
              deploy.trigger(JENKINS_DEPLOY_SITE_ROOT, repoName, jenkinsToken, ['chartVersion': tag, 'environment': environment, 'helmChartRepoType': HELM_CHART_REPO_TYPE])
            }
          }
        }
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

      stage('Publish to Log Analytics') {
        consoleLogs.save('/var/log/jenkins/console')
      }
    }
  }
}
