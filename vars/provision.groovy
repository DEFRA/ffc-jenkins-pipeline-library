import uk.gov.defra.ffc.Provision

def createResources(repoName, pr) {
  Provision.createResources(this, repoName, pr)
}

def deleteBuildResources(repoName, pr) {
  Provision.deleteQueues(this, repoName, pr))
}
