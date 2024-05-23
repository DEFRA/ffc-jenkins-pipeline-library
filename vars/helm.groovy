import uk.gov.defra.ffc.Helm

String getExtraCommands(String tag) {
  return Helm.getExtraCommands(tag)
}

String getPrCommands(String registry, String chartName, String tag) {
  return Helm.getPrCommands(registry, chartName, tag, BUILD_NUMBER)
}

void deployChart(String environment, String registry, String chartName, String tag, String pr) {
  Helm.deployChart(this, environment, registry, chartName, tag, pr)
}

void undeployChart(String environment, String chartName, String tag) {
  Helm.undeployChart(this, environment, chartName, tag)
}

void publishChart(String registry, String chartName, String tag, String helmChartRepoType="artifactory", Boolean overwriteImagePath=true) {
  if (helmChartRepoType) {
    switch (helmChartRepoType.toLowerCase()) {
      case 'artifactory':
        Helm.publishChart(this, registry, chartName, tag)
        break
      case 'acr':
        Helm.publishChartToACR(this, registry, chartName, tag, overwriteImagePath)
        break
      default:
        throw new Exception("Unknown Helm chart location: $helmChartRepoType")
    }
  } else {
    Helm.publishChart(this, registry, chartName, tag)
  }
}

void deployRemoteChart(String environment, String namespace, String chartName, String chartVersion, String helmChartRepoType='acr') {
  if (helmChartRepoType) {
    switch (helmChartRepoType.toLowerCase()) {
      case 'artifactory':
        Helm.deployRemoteChart(this, environment, namespace, chartName, chartVersion)
        break
      case 'acr':
        Helm.deployRemoteChartFromACR(this, environment, namespace, chartName, chartVersion)
        break
      default:
        throw new Exception("Unknown Helm chart location: $helmChartRepoType")
    }
  } else {
    Helm.deployRemoteChart(this, environment, namespace, chartName, chartVersion)
  }
}
