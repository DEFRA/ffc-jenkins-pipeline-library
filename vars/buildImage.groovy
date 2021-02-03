def call(Map config=[:]) {
  String repoName = ''
  String pr = ''
  String tag = ''
  String mergedPrNo = ''
  String imageName = ''
  String imageNameLatest = ''
  String defaultBranch = 'main'
  String versionFileName = 'VERSION'
  String containerSrcFolder = '\\/home\\/node'
  String nodeDevelopmentImage = 'defradigital/node-development'
  Boolean tagExists = false

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

      stage('Set image name') {
        imageName = build.getImageName(repoName, tag, config.tagSuffix, config.registry)
        imageNameLatest = build.getImageName(repoName, 'latest', null, config.registry)
      }

      stage("Build image") {
        build.buildContainerImage(imageName)
        build.buildContainerImage(imageNameLatest)
      }

      if(pr == '') {
        stage("Check if tag exists") {
          tagExists = build.containerTagExists(imageName)
        }

        if (!tagExists) {
          stage("Push image") {
            build.pushContainerImage(imageName)
            build.pushContainerImage(imageNameLatest)
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
    }
  }
}
