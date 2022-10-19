import uk.gov.defra.ffc.ADO

void triggerPipeline(String namespace, String chartName, String chartVersion, Boolean hasDatabase) {
  ADO.triggerPipeline(this, namespace, chartName, chartVersion, hasDatabase)
}
