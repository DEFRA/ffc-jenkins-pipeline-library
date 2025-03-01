package uk.gov.defra.ffc

import uk.gov.defra.ffc.Utils
import groovy.json.JsonOutput

class Release implements Serializable {
  /**
   * Checks GitHub to determine if a given Release Tag already exists for that repo.
   *
   * Takes three parameters:
   * - the version tag in SemVer format to check for on GitHub e.g 1.0.0
   * - the repository name to check
   * - the GitHub connection token secret text
   */
  private static def exists(ctx, versionTag, repoName, token) {
    try {
      return ctx.sh(returnStdout: true, script: "curl -s -H 'Authorization: token $token' https://api.github.com/repos/DEFRA/$repoName/releases/tags/$versionTag | jq '.tag_name'").trim().replaceAll (/"/, '') == versionTag ? true : false
    }
    catch(Exception ex) {
      ctx.echo('Failed to check release status on github')
      throw new Exception (ex)
    }
  }

  /**
   * Attaches a tag to a specified commit on a repo in the DEFRA GitHub
   * account. If the provided tag already exists on origin, it is deleted and
   * reattached to the given commit SHA. If the tag does not exist on origin,
   * it is created and pushed to origin.
   *
   * It takes three parameters:
   * - a string containing the tag to attach to the commit
   * - a string containing the commit SHA
   * - a string containing the name of the repository (assumed to be in the
   *   DEFRA GitHub account)
   */
  private static def tagCommit(ctx, tag, commitSha, repoName) {
    ctx.dir('attachTag') {
      ctx.withCredentials([ctx.string(credentialsId: 'github-ffcplatform-access-token', variable: 'token')]) {
        ctx.git(url: "https://${ctx.token}@github.com/DEFRA/${repoName}.git")
        ctx.sh("git push origin :refs/tags/$tag")
        ctx.sh("git tag -f $tag $commitSha")
        ctx.sh("git push origin $tag")
      }
      ctx.deleteDir()
    }
  }

  static def trigger(ctx, versionTag, repoName, releaseDescription, token, prerelease=false) {
    if (exists(ctx, versionTag, repoName, token)) {
      ctx.echo("Release $versionTag already exists")
      return false
    }

    ctx.echo("Triggering release $versionTag for $repoName")
    boolean result = false

    // create json body for GitHub curl request, using JsonOutput will automatically escape all special characters
    def releaseBody = JsonOutput.toJson(["tag_name":versionTag, "target_commitish": ctx.BRANCH_NAME, "name": "Release ${versionTag}", "body": "${releaseDescription}", "prerelease":prerelease])

    // saving JSON to a file avoids the issue of escaping the already escaped characters and the special characters behind them in shell command
    ctx.sh('mkdir -p -m 777 release-data')
    ctx.dir('release-data') {
      ctx.writeFile([file: 'releaseData.txt', text: releaseBody, encoding: "UTF-8"])
      def script = "curl -v -X POST -H 'Authorization: token $token' -H 'Content-type: application/json' -d @releaseData.txt https://api.github.com/repos/DEFRA/$repoName/releases"
      result = ctx.sh(returnStdout: true, script: script)
    }

    if (exists(ctx, versionTag, repoName, token)) {
      ctx.echo('Release Successful')
    } else {
      throw new Exception('Release failed')
    }

    return true
  }

  static def addSemverTags(ctx, version, repoName) {
    def versionList = version.tokenize('.')
    assert versionList.size() == 3

    def majorTag = "${versionList[0]}"
    def minorTag = "${versionList[0]}.${versionList[1]}"
    def commitSha = Utils.getCommitSha(ctx)

    tagCommit(ctx, minorTag, commitSha, repoName)
    tagCommit(ctx, majorTag, commitSha, repoName)
  }
}
