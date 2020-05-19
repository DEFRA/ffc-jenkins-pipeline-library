package uk.gov.defra.ffc

class Release implements Serializable {

  static def exists(ctx, versionTag, repoName, token){
    try {
      def result = ctx.sh(returnStdout: true, script: "curl -s -H 'Authorization: token $token' https://api.github.com/repos/DEFRA/$repoName/releases/tags/$versionTag | jq '.tag_name'").trim().replaceAll (/"/, '') == "$versionTag" ? true : false
      return result
    }
    catch(Exception ex) {
      ctx.echo("Failed to check release status on github")
      throw new Exception (ex)
    }
  }

  static def tagCommit(ctx, tag, commitSha, repoName) {
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
}
