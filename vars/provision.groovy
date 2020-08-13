import uk.gov.defra.ffc.Provision

def createResources(environment, repoName, pr) {
  Provision.createResources(this, environment, repoName, pr)
}

def deleteBuildResources(environment, repoName, pr) {
  Provision.deleteBuildResources(this, environment, repoName, pr)
}

def createPrDatabase(environment, repoName, pr){
  return Provision.createPrDatabase(this, environment, repoName, pr)
}
