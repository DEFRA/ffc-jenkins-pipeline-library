import uk.gov.defra.ffc.Utils

def trigger(jenkinsUrl, repoName, token, params) {
  def folder = Utils.getFolder(repoName)
  def url = "$jenkinsUrl/job/$folder/job/$repoName-deploy/buildWithParameters?token=$token"
  params.each { param ->
    url = url + "\\&$param.key=$param.value"
  }
  echo("Triggering deployment for $url")
  sh(script: "curl -fk $url")
}
