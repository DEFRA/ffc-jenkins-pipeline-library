import uk.gov.defra.ffc.Database

def provisionPrDbRoleAndSchema(host, dbName, jenkinsUserCredId, prUserCredId, prCode, useIfNotExists=false) {
  return Database.provisionPrDbRoleAndSchema(this, host, dbName, jenkinsUserCredId, prUserCredId, prCode, useIfNotExists)
}

def destroyPrDbRoleAndSchema(host, dbName, jenkinsUserCredId, prCode) {
  return Database.destroyPrDbRoleAndSchema(this, host, dbName, jenkinsUserCredId, prCode)
}
