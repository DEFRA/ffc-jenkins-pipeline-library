import uk.gov.defra.ffc.Cleanup

def call(Map config=[:]) {
  node {
    stage('delete resources of closed PR') {
      Cleanup.kubernetesResources(this, config.environment, repoName)
    }
  }
}

