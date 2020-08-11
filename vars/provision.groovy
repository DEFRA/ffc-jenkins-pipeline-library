import uk.gov.defra.ffc.Provision

def createResources(environment, repoName, pr) {
  Provision.createResources(this, environment, repoName, pr)
}

def deleteBuildResources(environment, repoName, pr) {
  Provision.deleteBuildResources(this, environment, repoName, pr)
}


def getDatabaseEnvVars(environment){
  return Provision.getDatabaseEnvVars(this, environment)
}

def getMigrationFiles(){
  def schemaUp = libraryResource 'migration/schema-up'
  echo schemaUp
  echo Provision.getMigrationFiles()
}
