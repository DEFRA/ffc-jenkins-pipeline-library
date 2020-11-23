import uk.gov.defra.ffc.Deploy

void trigger(String jenkinsUrl, String repoName, String token, def params) {
  Deploy.trigger(this, jenkinsUrl, repoName, token, params)
}
