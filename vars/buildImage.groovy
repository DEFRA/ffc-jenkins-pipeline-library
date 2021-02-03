def call(Map config=[:]) {
  node {
    try {
      String repoName = ''
      String pr = ''
      String tag = ''
      String mergedPrNo = ''
      String defaultBranch = 'main'
      String versionFileName = 'VERSION'
      String nodeDevelopmentImage = 'defradigital/node-development'

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
        def version = version.getFileVersion(versionFileName)
        (repoName, pr, tag, mergedPrNo) = build.getVariables(version, defaultBranch)
      }

      if (pr != '') {
        stage('Verify version incremented') {
          version.verifyFileIncremented(versionFileName)
        }
      }

      if (config.containsKey('validateClosure')) {
        config['validateClosure']()
      }

      // TODO BUILD

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
    }
  }
}
