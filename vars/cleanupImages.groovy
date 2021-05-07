import uk.gov.defra.ffc.Cleanup

void call(Map config=[:]) {
  node {
    stage('delete dangling images from ACR') {
      Cleanup.danglingImages(this)
    }
  }
}

