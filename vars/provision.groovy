import uk.gov.defra.ffc.Provision

def createResources(environment, repoName, pr) {
  Provision.createResources(this, environment, repoName, pr)
}

def deleteBuildResources(repoName, pr) {
  Provision.deleteBuildResources(this, repoName, pr)
}
