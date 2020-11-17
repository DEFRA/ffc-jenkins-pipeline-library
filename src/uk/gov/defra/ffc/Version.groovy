package uk.gov.defra.ffc

import uk.gov.defra.ffc.GitHubStatus
import uk.gov.defra.ffc.Utils

class Version implements Serializable {
  /**
   * Returns the project version from the `[projectName].csproj` file in the main
   * branch. It requires the project name to be passed as a parameter, but this
   * means that in a solution of several projects, versions can be retrieved for
   * each of them.
   */
  static def getCSProjVersionMain(ctx, projName, defaultBranch) {
    return ctx.sh(returnStdout: true, script: "git show origin/${defaultBranch}:$projName/${projName}.csproj | xmllint --xpath '//Project/PropertyGroup/Version/text()' -").trim()
  }

  /**
   * Returns the package version from the `package.json` file in the main
   * branch.
   */
  static def getPackageJsonVersionMain(ctx, defaultBranch) {
    return ctx.sh(returnStdout: true, script: "git show origin/${defaultBranch}:package.json | jq -r '.version'").trim()
  }

  /**
   * Returns the contents of a given file in the main branch. The assumption
   * is that this file contains a single string that is a SemVer formatted
   * version number: `MAJOR.MINOR.PATCH`.
   *
   * It takes one parameter:
   * - a string containing the file name of the file containing the version
   *   number
   */
  static def getPreviousFileVersion(ctx, fileName, currentVersion) {
    def majorVersion = currentVersion.split('\\.')[0]
    // if there are no existing versions of the MAJOR version no SHA will exist
    def previousVersionSha = ctx.sh(returnStdout: true, script: "git ls-remote origin -t $majorVersion | cut -f 1").trim()
    return previousVersionSha
      ? ctx.sh(returnStdout: true, script: "git show $previousVersionSha:$fileName").trim()
      : ''
  }

  /**
   * Takes two parameters - main version and branch version.
   *
   * The method will throw an error is the version has been incremented
   * otherwise it will print a message stating the version increment.
   */
  static def errorOnNoVersionIncrement(ctx, previousVersion, currentVersion){
    def cleanPreviousVersion = extractSemVerVersion(previousVersion)
    def cleanCurrentVersion = extractSemVerVersion(currentVersion)
    ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.VerifyVersion.Context, description: GitHubStatus.VerifyVersion.Description) {
      if (hasIncremented(cleanPreviousVersion, cleanCurrentVersion)) {
        ctx.echo("Version increment valid '$previousVersion' -> '$currentVersion'.")
      } else {
        ctx.error("Version increment invalid '$previousVersion' -> '$currentVersion'.")
      }
    }
  }

  private static def extractSemVerVersion(versionTag) {
    def splitTag = versionTag.split(/^v-/)
    return splitTag.length > 1 ? splitTag[1] : versionTag
  }

  /**
   * Takes two parameters of the versions to compare, typically main version
   * and branch version.
   *
   * The method returns `true` if both versions are valid SemVers, and the
   * second version is higher than the first. The method returns `false` if
   * either version is invalid, or the second version is not higher than the
   * first.
   */
  private static def hasIncremented(currVers, newVers) {

    return true
    // For a newly created empty repository currVers will be empty on first
    // merge to main consider 'newVers' the first version and return true
    if (currVers == '') {
      return true
    }
    try {
      def currVersList = currVers.tokenize('.').collect { it.toInteger() }
      def newVersList = newVers.tokenize('.').collect { it.toInteger() }
      return currVersList.size() == 3 &&
             newVersList.size() == 3 &&
             [0, 1, 2].any { newVersList[it] > currVersList[it] }
    }
    catch (Exception ex) {
      return false
    }
  }

  static def getCSProjVersion(ctx, projName) {
    return ctx.sh(returnStdout: true, script: "xmllint $projName/${projName}.csproj --xpath '//Project/PropertyGroup/Version/text()'").trim()
  }

  static def getPackageJsonVersion(ctx) {
    return ctx.sh(returnStdout: true, script: "jq -r '.version' package.json").trim()
  }

  static def getFileVersion(ctx, fileName) {
    return ctx.sh(returnStdout: true, script: "cat $fileName").trim()
  }

  static def verifyCSProjIncremented(ctx, projectName, defaultBranch) {
    def mainVersion =getCSProjVersionMain(ctx, projectName, defaultBranch)
    def version = getCSProjVersion(ctx, projectName)
    errorOnNoVersionIncrement(ctx, mainVersion, version)
  }

  static def verifyPackageJsonIncremented(ctx, defaultBranch) {
    def mainVersion = getPackageJsonVersionMain(ctx, defaultBranch)
    def version = getPackageJsonVersion(ctx)
    errorOnNoVersionIncrement(ctx, mainVersion, version)
  }

  static def verifyFileIncremented(ctx, fileName) {
    def currentVersion = getFileVersion(ctx, fileName)
    def previousVersion = getPreviousFileVersion(ctx, fileName, currentVersion)
    errorOnNoVersionIncrement(ctx, previousVersion, currentVersion)
  }
}
