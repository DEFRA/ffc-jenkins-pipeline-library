void call(Map config=[:]) {
  def pr = ''
  def repoName = ''
  String tag = ''
  String defaultBranch = 'main'
  String environment = 'snd'
  Boolean triggerDeployment = config.triggerDeployment != null ? config.triggerDeployment : true
  String deploymentPipelineName = ''

  node {
    try {
      stage('Ensure clean workspace') {
          deleteDir()
        }

      stage('Checkout source code') {
        build.checkoutSourceCode(defaultBranch)
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

      stage('Helm lint') {
        test.lintHelm(repoName)
      }

      if (pr != '') {
        stage('Helm install') {
          helm.deployChart(environment, DOCKER_REGISTRY, repoName, tag, pr)
        }
      } else {
        stage('Publish chart') {
          helm.publishChart(DOCKER_REGISTRY, repoName, tag, HELM_CHART_REPO_TYPE)
        }

        stage('Trigger GitHub release') {
          withCredentials([
            string(credentialsId: 'github-auth-token', variable: 'gitToken')
          ]) {
            String commitMessage = utils.getCommitMessage()
            release.trigger(tag, repoName, commitMessage, gitToken)
          }
        }

        if (triggerDeployment) {
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
      }
    } catch(e) {
      notifySlack.buildFailure(e.message, "#generalbuildfailures")
      throw e
    } finally {

      stage('Publish to Log Analytics') {
        consoleLogs.save('/var/log/jenkins/console')
      }
    }
  }
}
