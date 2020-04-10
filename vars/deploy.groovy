// public
def trigger(jenkinsUrl, repoName, token, params) {
  def folder = getFolder(repoName)
  def url = "$jenkinsUrl/job/$folder/job/$repoName-deploy/buildWithParameters?token=$token"
  params.each { param ->
    url = url + "\\&$param.key=$param.value"
  }
  echo "Triggering deployment for $url"
  sh(script: "curl -k $url")
}

def folder(repoName) {
  def folderArray = repoName.split('-')
  return "${folderArray[0]}-${folderArray[1]}"
}
