import uk.gov.defra.ffc.Provision

def createResources(environment, repoName, pr) {
  Provision.createResources(this, environment, repoName, pr)
}

def deleteBuildResources(environment, repoName, pr) {
  Provision.deleteBuildResources(this, environment, repoName, pr)
}


def getMigrationEnvVars(environment, repoName, pr){
  return Provision.getMigrationEnvVars(this, environment, repoName, pr)
}

def getMigrationFiles(destinationFolder){
  echo Provision.getMigrationFiles(this, destinationFolder)
}
