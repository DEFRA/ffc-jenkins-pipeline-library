def call(Map config=[:]) {
  String repoName = ''
  String pr = ''
  String tag = ''
  String mergedPrNo = ''
  String defaultBranch = 'main'
  String containerSrcFolder = '\\/home\\/node'
  String nodeDevelopmentImage = 'defradigital/node-development'

  node {
    try {
      stage('Ensure clean workspace') {
        deleteDir()
      }

      stage('Set default branch') {
        defaultBranch = build.getDefaultBranch(defaultBranch, config.defaultBranch)
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
      }

      if (config.containsKey('validateClosure')) {
        config['validateClosure']()
      }

      if(pr != '') {
        stage("Publish to Npm (Next)") {
          def version = version.getPackageJsonVersion()
          package.publishToNpm("${version}-alpha.${BUILD_NUMBER}")
        }
      } else {
        stage("Publish to Npm") {
          package.publishToNpm()
        }

        stage('Trigger GitHub release') {
          withCredentials([
            string(credentialsId: 'github-auth-token', variable: 'gitToken')
          ]) {
            String commitMessage = utils.getCommitMessage()
            release.trigger(tag, repoName, commitMessage, gitToken)            
          }
        }
      }

      if (config.containsKey('buildClosure')) {
        config['buildClosure']()
      }
    } catch (err) {
      def errMsg = utils.getErrorMessage(err)
      echo("Build failed with message: $errMsg")

      stage('Send build failure slack notification') {
        notifySlack.buildFailure('#generalbuildfailures', defaultBranch)
      }

      if (config.containsKey('failureClosure')) {
        config['failureClosure']()
      }
      throw err
    } finally {
      stage('Change ownership of outputs') {
        test.changeOwnershipOfWorkspace(nodeDevelopmentImage, containerSrcFolder)
      }

      if (config.containsKey('finallyClosure')) {
        config['finallyClosure']()
      }

      stage('Publish to Log Analytics') {
        consoleLogs.save(JENKINS_DEPLOY_SITE_ROOT, repoName, BRANCH_NAME, BUILD_NUMBER, '/var/log/jenkins/console')
      }
    }
  }
}
