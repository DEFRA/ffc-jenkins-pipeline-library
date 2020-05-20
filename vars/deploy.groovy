import uk.gov.defra.ffc.Deploy

def trigger(jenkinsUrl, repoName, token, params) {
  Deploy.trigger(this, jenkinsUrl, repoName, token, params)
}
