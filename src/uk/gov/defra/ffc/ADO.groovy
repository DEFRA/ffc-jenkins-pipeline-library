package uk.gov.defra.ffc

class ADO implements Serializable {

  static void triggerPipeline(def ctx, String namespace, String chartName, String chartVersion, Boolean hasDatabase) {
    if (hasDatabase) {
      triggerDatabasePipeline(ctx, chartName, chartVersion)
    }
    triggerHelmPipeline(ctx, namespace, chartName, chartVersion)
  }

  static void triggerDatabasePipeline(def ctx, String database, String version) {
    ctx.echo "Triggering ADO Database pipeline for ${database} ${version}"
    String pipelineId = ctx.ADO_DATABASE_PIPELINE_ID
    def data = "'{\"templateParameters\": {\"database\":\"$database\",\"tagValue\":\"$version\"}}'"
    triggerBuild(ctx, pipelineId, data, database, version)
  }

  static void triggerHelmPipeline(def ctx, String namespace, String chartName, String chartVersion) {
    ctx.echo "Triggering ADO Helm pipeline for ${chartName} ${chartVersion} in ${namespace}"
    String pipelineId = ctx.ADO_HELM_PIPELINE_ID
    def data = "'{\"templateParameters\": {\"namespace\": \"$namespace\",\"chart\":\"$chartName\",\"chartVersion\":\"$chartVersion\"}}'"
    triggerBuild(ctx, pipelineId, data, chartName, chartVersion)
  }

  static void triggerBuild(def ctx, pipelineId, data, repository, version) {
    ctx.withCredentials([
      ctx.usernamePassword(credentialsId: 'ado-credentials', usernameVariable: 'username', passwordVariable: 'password')
    ]) {
      String token = getToken(ctx)
      String buildId = ctx.sh(returnStdout: true, script: "curl -u $ctx.username:$ctx.token https://dev.azure.com/defragovuk/DEFRA-FFC/_apis/pipelines/${pipelineId}/runs?api-version=6.0-preview.1 -H 'Content-Type: application/json' -d $data | jq '.id'").trim()
      if(buildId) {
        tagBuild(ctx, buildId, repository, version)
      }
    }
  }

  static void tagBuild(def ctx, String buildId, String repository, String version) {
    ctx.withCredentials([
      ctx.usernamePassword(credentialsId: 'ado-credentials', usernameVariable: 'username', passwordVariable: 'password')
    ]) {
      String token = getToken(ctx)
      ctx.echo "Tagging build ${buildId} with ${repository} ${version}"
      def data = "'[\"$repository\", \"$version\"]'"
      ctx.sh("curl -u ${ctx.username}:${ctx.token} https://dev.azure.com/defragovuk/DEFRA-FFC/_apis/build/builds/${buildId}/tags?api-version=6.0 -H 'Content-Type: application/json' -d ${data} ")
    }
  }

  static String getToken(ctx) {
    String getTokenCommand = "az account get-access–token —resource 499b84ac-1321-427f-aa17-267ca6975798 | | jq '.token'"
    String token = ctx.sh(returnStdout: true, script: "$getTokenCommand").trim()
    return token
  }
}
