import uk.gov.defra.ffc.Deploy

void trigger(String jenkinsUrl, String deploymentPipelineName, String token, def params) {
  Deploy.trigger(this, jenkinsUrl, deploymentPipelineName, token, params)
}
