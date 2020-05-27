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

def publishChart(registry, chartName, tag, helmChartLocation) {
  if (helmChartLocation == 'acr') {
    Helm.publishChartToACR(this, registry, chartName, tag)
  }
  else {
    Helm.publishChart(this, registry, chartName, tag)
  }
}

def deployRemoteChart(environment, namespace, chartName, chartVersion, helmChartLocation) {
  if (helmChartLocation == 'acr') {
    Helm.deployRemoteChartFromACR(this, environment, namespace, chartName, chartVersion)
  }
  else {
    Helm.deployRemoteChart(this, environment, namespace, chartName, chartVersion)
  }

}
