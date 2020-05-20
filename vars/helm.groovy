import uk.gov.defra.ffc.Helm

def getExtraCommands(tag) {
  return Helm.getExtraCommands(tag)
}

def getPrCommands(registry, chartName, tag) {
  return Helm.getPrCommands(registry, chartName, tag, BUILD_NUMBER)
}

def deployChart(environment, registry, chartName, tag) {
  Helm.deployChart(this, environment, registry, chartName, tag)
}

def undeployChart(environment, chartName, tag) {
  Helm.undeployChart(this, environment, chartName, tag)
}

def publishChart(registry, chartName, tag) {
  Helm.publishChart(this, registry, chartName, tag)
}

def publishChartToACR(registry, chartName, tag) {
  Helm.publishChartToACR(this, registry, chartName, tag)
}

def deployRemoteChart(environment, namespace, chartName, chartVersion) {
  Helm.deployRemoteChart(this, environment, namespace, chartName, chartVersion)
}

def deployRemoteChartFromACR(environment, namespace, chartName, chartVersion) {
  Helm.deployRemoteChartFromACR(this, environment, namespace, chartName, chartVersion)
}
