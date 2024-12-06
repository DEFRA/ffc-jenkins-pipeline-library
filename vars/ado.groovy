import uk.gov.defra.ffc.ADO

void triggerPipeline(String namespace, String repoName, String chartVersion, Boolean hasDatabase) {
  ADO.triggerPipeline(this, namespace, repoName, chartVersion, hasDatabase)
}

void triggerNewFFCPipeline(String namespace, String chartName, String chartVersion) {
  ADO.triggerNewFFCPipeline(this, namespace, chartName, chartVersion)
}
