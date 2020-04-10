// public
def trigger(jenkinsUrl, repoName, token, params) {
  def folder = getFolder(repoName)
  def url = "$jenkinsUrl/job/$repoName-deploy/buildWithParameters?token=$token"
  params.each { param ->
    url = url + "\\&$param.key=$param.value"
  }
  echo "Triggering deployment for $url"
  sh(script: "curl -k $url")
}
