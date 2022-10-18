package uk.gov.defra.ffc

class ADO implements Serializable {

  static void triggerPipeline(def ctx, String namespace, String chartName, String chartVersion, Boolean hasDatabase) {
    if (hasDatabase) {
      triggerDatabasePipeline(ctx, chartName, chartVersion)
    }
    triggerHelmPipeline(ctx, namespace, chartName, chartVersion)
  }

  static void triggerDatabasePipeline(def ctx, String database, String version) {
    ctx.withCredentials([
      ctx.usernamePassword(credentialsId: 'ado-credentials', usernameVariable: 'username', passwordVariable: 'password')
    ]) {
      ctx.echo "Triggering ADO Database pipeline for ${database} ${version}"
      String pipelineId = ctx.ADO_DATABASE_PIPELINE_ID
      String buildId = ctx.sh(returnStdout: true, script: "curl -u $ctx.username:$ctx.password https://dev.azure.com/defragovuk/DEFRA-FFC/_apis/pipelines/${pipelineId}/runs?api-version=6.0-preview.1 -H 'Content-Type: application/json' -d '{\"templateParameters\": {\"database\":\"$database\",\"tagValue\":\"$version\"}}' | jq '.id'").trim()
      ctx.echo "BuildId ${buildId}"
      tagBuild(ctx, buildId, database, version)
    }
  }

  static void triggerHelmPipeline(def ctx, String namespace, String chartName, String chartVersion) {
    ctx.withCredentials([
      ctx.usernamePassword(credentialsId: 'ado-credentials', usernameVariable: 'username', passwordVariable: 'password')
    ]) {
      ctx.echo "Triggering ADO Helm pipeline for ${chartName} ${chartVersion} in ${namespace}"
      String pipelineId = ctx.ADO_HELM_PIPELINE_ID
      String buildId = ctx.sh(returnStdout: true, script: "curl -u $ctx.username:$ctx.password https://dev.azure.com/defragovuk/DEFRA-FFC/_apis/pipelines/${pipelineId}/runs?api-version=6.0-preview.1 -H 'Content-Type: application/json' -d '{\"templateParameters\": {\"namespace\": \"$namespace\",\"chart\":\"$chartName\",\"chartVersion\":\"$chartVersion\"}}' | jq '.id'").trim()
      ctx.echo "BuildId ${buildId}"
      tagBuild(ctx, buildId, database, version)
    }
  }

  static void tagBuild(def ctx, String buildId, String repository, String version) {
    ctx.withCredentials([
      ctx.usernamePassword(credentialsId: 'ado-credentials', usernameVariable: 'username', passwordVariable: 'password')
    ]) {
      ctx.sh("curl -u $ctx.username:$ctx.password https://dev.azure.com/defragovuk/DEFRA-FFC/_apis/build/builds/${buildId}/tags?api-version=6.0-preview.1 -H 'Content-Type: application/json' -d '[\"$repository\", \"$version\"]'")
    }
  }
}
