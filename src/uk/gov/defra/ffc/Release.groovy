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
  private static def exists(ctx, versionTag, repoName, token){
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
      ctx.sshagent(['ffc-jenkins-pipeline-library-deploy-key']) {
        ctx.git(credentialsId: 'ffc-jenkins-pipeline-library-deploy-key', url: "git@github.com:DEFRA/${repoName}.git")
        ctx.sh("git push origin :refs/tags/$tag")
        ctx.sh("git tag -f $tag $commitSha")
        ctx.sh("git push origin $tag")
      }
      ctx.deleteDir()
    }
  }

  static def trigger(ctx, versionTag, repoName, releaseDescription, token){
   
    if (exists(ctx, versionTag, repoName, token)) {
      ctx.echo("Release $versionTag already exists")
      return false
    }

    ctx.echo("Triggering release $versionTag for $repoName")
    boolean result = false

    def json = JsonOutput.toJson('{ \"tag_name\" : \"$versionTag\", \"name\" : \"Release $versionTag\", \"body\" : \" $releaseDescription \" }')

    result = ctx.sh(returnStdout: true, script: "curl -v -X POST -H 'Authorization: token $token' -d $json https://api.github.com/repos/DEFRA/$repoName/releases")    

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
