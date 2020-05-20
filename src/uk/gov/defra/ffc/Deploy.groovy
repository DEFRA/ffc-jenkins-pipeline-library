package uk.gov.defra.ffc

import uk.gov.defra.ffc.Utils

class Deploy implements Serializable {
  static def trigger(ctx, jenkinsUrl, repoName, token, params) {
    def folder = Utils.getFolder(repoName)
    def url = "$jenkinsUrl/job/$folder/job/$repoName-deploy/buildWithParameters?token=$token"
    params.each { param ->
      url = url + "\\&$param.key=$param.value"
    }
    ctx.echo("Triggering deployment for $url")
    ctx.sh(script: "curl -fk $url")
  }
}
