import uk.gov.defra.ffc.Database

def provisionPrDbRoleAndSchema(String host, String dbName, String jenkinsUserCredId, String prUserCredId, String prCode, Boolean useIfNotExists=false) {
  return Database.provisionPrDbRoleAndSchema(this, host, dbName, jenkinsUserCredId, prUserCredId, prCode, useIfNotExists)
}

def destroyPrDbRoleAndSchema(String host, String dbName, String jenkinsUserCredId, String prCode) {
  return Database.destroyPrDbRoleAndSchema(this, host, dbName, jenkinsUserCredId, prCode)
}

def runRemoteMigrations(String environment, String repoName, String version) {
  Database.runRemoteMigrations(this, environment, repoName, version)
}
