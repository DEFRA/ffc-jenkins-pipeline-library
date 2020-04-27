def call(Map config=[:], Closure body={}) {
  def containerTag = ''
  def mergedPrNo = ''
  def pr = ''
  def repoName = ''

  node {
    checkout scm
    try {
      stage('Set GitHub status as pending') {
        build.setGithubStatusPending()
      }
      stage('Set PR, and containerTag variables') {
        (repoName, pr, containerTag, mergedPrNo) = build.getVariables(version.getCSProjVersion(config.project))
      }
      if (pr != '') {
        stage('Verify version incremented') {
          version.verifyCSProjIncremented(config.project)
        }
      }

      if (config.containsKey("validateClosure")) {
        config["validateClosure"]()
      }

      stage('Helm lint') {
        test.lintHelm(repoName)
      }

      if (config.containsKey("buildClosure")) {
        config["buildClosure"]()
      }

      stage('Build test image') {
        build.buildTestImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, BUILD_NUMBER)
      }
      stage('Run tests') {
        build.runTests(repoName, repoName, BUILD_NUMBER)
      }

      if (config.containsKey("testClosure")) {
        config["testClosure"]()
      }

      stage('Push container image') {
        build.buildAndPushContainerImage(DOCKER_REGISTRY_CREDENTIALS_ID, DOCKER_REGISTRY, repoName, containerTag)
      }
      if (pr != '') {
        stage('Helm install') {
          helm.deployChart(config.environment, DOCKER_REGISTRY, repoName, containerTag)
        }
      }
      else {
        stage('Publish chart') {
          helm.publishChart(DOCKER_REGISTRY, repoName, containerTag)
        }
        stage('Trigger GitHub release') {
          withCredentials([
            string(credentialsId: 'github-auth-token', variable: 'gitToken')
          ]) {
            release.trigger(containerTag, repoName, containerTag, gitToken)
          }
        }
        stage('Trigger Deployment') {
          withCredentials([
            string(credentialsId: "$repoName-deploy-token", variable: 'jenkinsToken')
          ]) {
            deploy.trigger(JENKINS_DEPLOY_SITE_ROOT, repoName, jenkinsToken, ['chartVersion': containerTag])
          }
        }
      }

      if (mergedPrNo != '') {
        stage('Remove merged PR') {
          helm.undeployChart(config.environment, repoName, mergedPrNo)
        }
      }

      if (config.containsKey("deployClosure")) {
        config["deployClosure"]()
      }

      body()
      stage('Set GitHub status as success'){
        build.setGithubStatusSuccess()
      }
    } catch(e) {stage('Set GitHub status as fail') {
        build.setGithubStatusFailure(e.message)
      }

      stage('Send build failure slack notification') {
        notifySlack.buildFailure(e.message, "#generalbuildfailures")
      }

      if (config.containsKey("failureClosure")) {
        config["failureClosure"]()
      }
      throw e
    } finally {
      stage('Clean up test output') {
        test.deleteOutput(repoName, containerSrcFolder)
      }

      if (config.containsKey("finallyClosure")) {
        config["finallyClosure"]()
      }
    }
  }
}
