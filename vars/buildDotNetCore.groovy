def call(Map config=[:]) {
  def tag = ''
  def mergedPrNo = ''
  def pr = ''
  def repoName = ''
  def csProjVersion = ''

  node {
    try {
      stage('Checkout source code') {
        build.checkoutSourceCode()
      }
      stage('Set PR, and tag variables') {
        csProjVersion = version.getCSProjVersion(config.project)
        (repoName, pr, tag, mergedPrNo) = build.getVariables(csProjVersion)
      }
      if (pr != '') {
        stage('Verify version incremented') {
          version.verifyCSProjIncremented(config.project)
        }
      }

      if (config.containsKey('validateClosure')) {
        config['validateClosure']()
      }


      stage('Helm lint') {
        test.lintHelm(repoName)
      }

      if (config.containsKey('buildClosure')) {
        config['buildClosure']()
      }

      stage('Build test image') {
        build.buildTestImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, BUILD_NUMBER, tag)
      }

      stage('Provision resources') {
        provision.createResources(config.environment, repoName, pr)
      }

      if (fileExists('./docker-compose.snyk.yaml')){
        stage('Snyk test') {
          // ensure obj folder exists and is writable by all
          sh("chmod 777 ${config.project}/obj || mkdir -p -m 777 ${config.project}/obj")
          build.extractSynkFiles(repoName, BUILD_NUMBER, tag)
          build.snykTest(config.snykFailOnIssues, config.snykOrganisation, config.snykSeverity, "${config.project}.sln", pr)
        }
      }

      stage('Run tests') {
        build.runTests(repoName, repoName, BUILD_NUMBER, tag, pr, config.environment)
      }

     stage('Publish pact broker') {
        pact.publishContractsToPactBroker(repoName, csProjVersion, utils.getCommitSha())
      }

      stage('SonarCloud analysis') {
        test.analyseDotNetCode(repoName, BRANCH_NAME, pr)
      }

      if (config.containsKey('testClosure')) {
        config['testClosure']()
      }

      stage('Push container image') {
        build.buildAndPushContainerImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, tag)
      }
      if (pr != '') {
        stage('Helm install') {
          helm.deployChart(config.environment, DOCKER_REGISTRY, repoName, tag, pr)
        }
      }
      else {
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
            deploy.trigger(JENKINS_DEPLOY_SITE_ROOT, repoName, jenkinsToken, ['chartVersion': tag, 'helmChartRepoType': HELM_CHART_REPO_TYPE])
          }
        }
      }

      if (config.containsKey('deployClosure')) {
        config['deployClosure']()
      }
    } catch(e) {
      echo("Build failed with message: $e.message")

      stage('Send build failure slack notification') {
        notifySlack.buildFailure('#generalbuildfailures')
      }

      if (config.containsKey('failureClosure')) {
        config['failureClosure']()
      }

      throw e
    } finally {

      stage('Clean up resources') {
        provision.deleteBuildResources(repoName, pr)
      }

      if (config.containsKey('finallyClosure')) {
        config['finallyClosure']()
      }
    }
  }
}
