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

def publishChart(registry, chartName, tag, helmChartLocation="artifactory") {
  if (helmChartLocation) {
    switch (helmChartLocation.toLowerCase()) {
      case 'artifactory':
        Helm.publishChart(this, registry, chartName, tag)
        break
      case 'acr':
        Helm.publishChartToACR(this, registry, chartName, tag)
        break
      default:
        throw new Exception("Unknown Helm chart location: $helmChartLocation")
    }
  }
  else {
    Helm.publishChart(this, registry, chartName, tag)
  }
}

def deployRemoteChart(environment, namespace, chartName, chartVersion, helmChartLocation="artifactory") {
  if (helmChartLocation) {
    switch (helmChartLocation.toLowerCase()) {
      case 'artifactory':
        Helm.deployRemoteChart(this, environment, namespace, chartName, chartVersion)
        break
      case 'acr':
        Helm.deployRemoteChartFromACR(this, environment, namespace, chartName, chartVersion)
        break
      default:
        throw new Exception("Unknown Helm chart location: $helmChartLocation")
    }
  }
  else {
    Helm.publishChart(this, registry, chartName, tag)
  }
}
