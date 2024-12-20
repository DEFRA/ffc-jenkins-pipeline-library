package uk.gov.defra.ffc

class ADO implements Serializable {

  static void triggerNewFFCPipeline(def ctx, String namespace, String repoName, String chartVersion) {
    ctx.echo "Triggering ADO FFC pipeline for ${repoName} ${chartVersion} in ${namespace}"
    String pipelineId = ctx.ADO_FFC_SERVICE_DEPLOY_PIPELINE_ID
    def data = "'{\"templateParameters\": {\"repoName\":\"$repoName\",\"version\":\"$chartVersion\",\"namespace\":\"$namespace\"}}'"
    triggerBuild(ctx, pipelineId, data, repoName, chartVersion)
  }

  static void triggerPipeline(def ctx, String namespace, String chartName, String chartVersion, Boolean hasDatabase) {
    if (hasDatabase) {
      triggerDatabasePipeline(ctx, namespace, chartName, chartVersion)
    }
    triggerHelmPipeline(ctx, namespace, chartName, chartVersion)
  }

  static void triggerDatabasePipeline(def ctx, String service, String database, String version) {
    ctx.echo "Triggering ADO Database pipeline for ${database} ${version}"
    String pipelineId = ctx.ADO_DATABASE_PIPELINE_ID
    def data = "'{\"templateParameters\": {\"databaseRepo\":\"$database\",\"version\":\"$version\",\"service\":\"$service\"}}'"
    triggerBuild(ctx, pipelineId, data, database, version)
  }

  static void triggerHelmPipeline(def ctx, String namespace, String chartName, String chartVersion) {
    ctx.echo "Triggering ADO Helm pipeline for ${chartName} ${chartVersion} in ${namespace}"
    String pipelineId = ctx.ADO_HELM_PIPELINE_ID
    def data = "'{\"templateParameters\": {\"helmChart\":\"$chartName\",\"chartVersion\":\"$chartVersion\",\"service\":\"$namespace\"}}'"
    triggerBuild(ctx, pipelineId, data, chartName, chartVersion)
  }

  static void triggerBuild(def ctx, pipelineId, data, repository, version) {
    ctx.withCredentials([
      ctx.usernamePassword(credentialsId: 'ado-credentials', usernameVariable: 'username', passwordVariable: 'password')
    ]) {
      String buildId = ctx.sh(returnStdout: true, script: "curl -u $ctx.username:$ctx.password https://dev.azure.com/defragovuk/DEFRA-FFC/_apis/pipelines/${pipelineId}/runs?api-version=6.0-preview.1 -H 'Content-Type: application/json' -d $data | jq '.id'").trim()
      if (buildId) {
        tagBuild(ctx, buildId, repository, version)
      }
    }
  }

  static void tagBuild(def ctx, String buildId, String repository, String version) {
    ctx.withCredentials([
      ctx.usernamePassword(credentialsId: 'ado-credentials', usernameVariable: 'username', passwordVariable: 'password')
    ]) {
      ctx.echo "Tagging build ${buildId} with ${repository} ${version}"
      def data = "'[\"$repository\", \"$version\"]'"
      ctx.sh("curl -u $ctx.username:$ctx.password https://dev.azure.com/defragovuk/DEFRA-FFC/_apis/build/builds/${buildId}/tags?api-version=6.0 -H 'Content-Type: application/json' -d ${data} ")
    }
  }

}
