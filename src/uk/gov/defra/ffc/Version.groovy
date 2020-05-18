package uk.gov.defra.ffc

class Version implements Serializable {
  static def getCSProjVersionMaster(ctx, projName) {
    return ctx.sh(returnStdout: true, script: "git show origin/master:$projName/${projName}.csproj | xmllint --xpath '//Project/PropertyGroup/Version/text()' -").trim()
  }

  static def getPackageJsonVersionMaster(ctx) {
    return ctx.sh(returnStdout: true, script: "git show origin/master:package.json | jq -r '.version'").trim()
  }

  static def getPreviousFileVersion(ctx, fileName, currentVersion) {
    def majorVersion = currentVersion.split('\\.')[0]
    // if there are no pre-existing versions of the MAJOR version no SHA will exist
    def previousVersionSha = ctx.sh(returnStdout: true, script: "git ls-remote origin -t $majorVersion | cut -f 1").trim()
    return previousVersionSha
      ? ctx.sh(returnStdout: true, script: "git show $previousVersionSha:$fileName").trim()
      : ''
  }

  static def errorOnNoVersionIncrement(ctx, previousVersion, currentVersion){
    def cleanPreviousVersion = Version.extractSemVerVersion(previousVersion)
    def cleanCurrentVersion = Version.extractSemVerVersion(currentVersion)
    if (Version.hasIncremented(cleanPreviousVersion, cleanCurrentVersion)) {
      ctx.echo("version increment valid '$previousVersion' -> '$currentVersion'")
    } else {
      ctx.error("version increment invalid '$previousVersion' -> '$currentVersion'")
    }
  }

  private static def extractSemVerVersion(versionTag) {
    def splitTag = versionTag.split(/^v-/)
    return splitTag.length > 1 ? splitTag[1] : versionTag
  }

  private static def hasIncremented(currVers, newVers) {
    // For a newly created empty repository currVers will be empty on first merge to master
    // consider 'newVers' the first version and return true
    if (currVers == '') {
      return true
    }
    try {
      currVersList = currVers.tokenize('.').collect { it.toInteger() }
      newVersList = newVers.tokenize('.').collect { it.toInteger() }
      return currVersList.size() == 3 &&
             newVersList.size() == 3 &&
             [0, 1, 2].any { newVersList[it] > currVersList[it] }
    }
    catch (Exception ex) {
      return false
    }
  }
}
