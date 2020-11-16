import uk.gov.defra.ffc.Deploy

def trigger(String jenkinsUrl, String repoName, String token, String params) {
  Deploy.trigger(this, jenkinsUrl, repoName, token, params)
}
