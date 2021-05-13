package uk.gov.defra.ffc

import uk.gov.defra.ffc.Utils

class Deploy implements Serializable {
  static def trigger(ctx, jenkinsUrl, deploymentPipelineName, token, params) {
    def folder = Utils.getFolder(ctx)
    def url = "$jenkinsUrl/job/$folder/job/$deploymentPipelineName/buildWithParameters?token=$token"
    params.each { param ->
      url = url + "\\&$param.key=$param.value"
    }
    ctx.echo("Triggering deployment for $url")
    ctx.sh(script: "curl -fk $url")
  }
}
