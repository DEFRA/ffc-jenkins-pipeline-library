import uk.gov.defra.ffc.Cleanup

void call(Map config=[:]) {
  node {
    stage('delete resources of closed PR') {
      Cleanup.prResources(this, config.environment, repoName, SOURCE_PROJECT_NAME)
    }
  }
}

