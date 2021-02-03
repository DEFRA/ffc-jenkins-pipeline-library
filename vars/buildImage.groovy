def call(Map config=[:]) {
  node {
    checkout scm
    try {
      String repoName = ''
      String pr = ''
      String tag = ''
      String mergedPrNo = ''
      String defaultBranch = 'main'
      String versionFileName = 'VERSION'

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
          version.verifyPackageJsonIncremented(defaultBranch)
        }
      }

      if (config.containsKey('validateClosure')) {
        config['validateClosure']()
      }

      // TODO BUILD

      if (config.containsKey('buildClosure')) {
        config['buildClosure']()
      }

        
        // imageMaps.each { ImageMap imageMap ->
        //   buildImages imageName: config.imageName, version: config.version, tagName: config.tagName, imageMap: imageMap, prTag: prTag
        
      
  } catch (err) {
      stage('Set GitHub status failure') {
        updateBuildStatus(err.message, 'FAILURE')
      }
      throw err
    }
  }
}
