def call(Map config=[:]) {
  String repoName = ''
  String pr = ''
  String tag = ''
  String mergedPrNo = ''
  String defaultBranch = 'main'
  String containerSrcFolder = '\\/home\\/node'
  String nodeDevelopmentImage = 'defradigital/node-development'
  String nodeTestVersion = "2.3.0-node20.15.0"
  String nodeTestImage = ''
  String localSrcFolder = '.'
  String lcovFile = './test-output/lcov.info'

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
      } else {
        stage('Rebuild all feature branches') {
          build.triggerMultiBranchBuilds(defaultBranch)
        }
      }

      if (config.containsKey('validateClosure')) {
        config['validateClosure']()
      }

      stage('npm audit') {
        build.npmAudit(config.npmAuditLevel, config.npmAuditLogType, config.npmAuditFailOnIssues, nodeDevelopmentImage, containerSrcFolder, pr)
      }

      stage('Snyk test') {
        build.snykTest(config.snykFailOnIssues, config.snykOrganisation, config.snykSeverity, pr)
      }

       if (fileExists('./test')) {
        stage('run test image') {
          build.runNodeTestImage(nodeTestImage, repoName)
        }

        stage('Create JUnit report') {
          test.createJUnitReport()
        }

        stage('Fix lcov report') {
          utils.replaceInFile(containerSrcFolder, localSrcFolder, lcovFile)
        }
      }

      stage('SonarCloud analysis') {
        test.analyseNodeJsCode(SONARCLOUD_ENV, SONAR_SCANNER, repoName, BRANCH_NAME, defaultBranch, pr)
      }

      if (config.containsKey('testClosure')) {
        config['testClosure']()
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
        notifySlack.buildFailure('generalbuildfailures', defaultBranch)
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
        consoleLogs.save('/var/log/jenkins/console')
      }
    }
  }
}
