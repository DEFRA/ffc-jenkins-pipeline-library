package uk.gov.defra.ffc

class ADO implements Serializable {

  static void triggerPipeline(String namespace, String chartName, String chartVersion) {
    ctx.withCredentials([
      ctx.usernamePassword(credentialsId: 'ado-credentials', usernameVariable: 'username', passwordVariable: 'password')
    ]) {
      ctx.echo "Triggering ADO pipeline for ${chartName} ${chartVersion} in ${namespace}"
      String pipelineId = ctx.ADO_HELM_PIPELINE_ID
      ctx.echo "Pipeline ID: ${pipelineId}"
      ctx.sh("curl -v -u $ctx.username:$ctx.password https://dev.azure.com/defragovuk/DEFRA-FFC/_apis/pipelines/${pipelineId}/runs?api-version=6.0-preview.1 -H 'Content-Type: application/json' -d '{\"templateParameters\": {\"namespace\": \"$namespace\",\"chart\":\"$chartName\",\"chartVersion\":\"$chartVersion\"}}'")
    }
  }
}
