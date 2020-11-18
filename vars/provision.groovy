import uk.gov.defra.ffc.Provision

def createResources(String environment, String repoName, String pr) {
  Provision.createResources(this, environment, repoName, pr)
}

def deleteBuildResources(String repoName, String pr) {
  Provision.deleteBuildResources(this, repoName, pr)
}
