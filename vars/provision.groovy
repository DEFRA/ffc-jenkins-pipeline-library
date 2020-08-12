import uk.gov.defra.ffc.Provision

def createResources(environment, repoName, pr) {
  Provision.createResources(this, environment, repoName, pr)
}

def deleteBuildResources(environment, repoName, pr) {
  Provision.deleteBuildResources(this, environment, repoName, pr)
}


def getDatabaseEnvVars(environment, repoName, pr){
  return Provision.getDatabaseEnvVars(this, environment, repoName, pr)
}

def getMigrationFiles(destinationFolder){
  echo Provision.getMigrationFiles(this, destinationFolder)
}
