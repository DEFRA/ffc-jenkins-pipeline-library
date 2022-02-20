void call(Map config=[:]) {
  String defaultBranch = 'main'
  String environment = 'snd'
  String containerSrcFolder = '\\/home\\/dotnet'
  String dotNetDevelopmentImage = 'defradigital/dotnetcore-development'
  String dotNetTestVersion = "1.2.12-dotnet6.0"
  String dotNetTestImage = ''
  String localSrcFolder = '.'
  String lcovFile = './test-output/lcov.info'
  String repoName = ''
  String pr = ''
  String tag = ''
  String mergedPrNo = ''

  node {
    try {
      stage('Ensure clean workspace') {
        deleteDir()
      }

      stage('Set default branch') {
        defaultBranch = build.getDefaultBranch(defaultBranch, config.defaultBranch)
      }

      stage('Set default .Net test version') {
        dotNetTestVersion = build.getImageTestVersion(dotNetTestVersion, config.dotNetTestVersion)
        dotNetTestImage = "${dotNetDevelopmentImage}:${dotNetTestVersion}"
      }

      stage('Set environment') {
        environment = config.environment != null ? config.environment : environment
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

      stage('Provision any required resources') {
         provision.createResources(environment, repoName, pr)
      }

      if (config.containsKey('buildClosure')) {
        config['buildClosure']()
      }


      if (fileExists('./test')) {
        stage('Run tests') {
          build.runDotNetTestImage(dotNetTestImage)
        }
      }


      stage('SonarCloud analysis') {
        test.analyseDotNetCode(repoName, config.project, BRANCH_NAME, defaultBranch, pr)
      }

      if (config.containsKey('testClosure')) {
        config['testClosure']()
      }

      stage('Provision function app') {
        withCredentials([
            string(credentialsId: 'github-auth-token', variable: 'gitToken')
          ]) {
            function.createFunctionResources(repoName, pr, gitToken, BRANCH_NAME)
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

      if (config.containsKey('deployClosure')) {
        config['deployClosure']()
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
        test.changeOwnershipOfWorkspace(dotNetDevelopmentImage, containerSrcFolder)
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
