import src.uk.gov.defra.ffc.Database
import src.uk.gov.defra.ffc.Utils

// The design rationale for the behaviour of this function is documented here:
// https://eaflood.atlassian.net/wiki/spaces/FPS/pages/1596653973/Creating+PR+database+namespaces+in+postgres
def provisionPrDbRoleAndSchema(host, dbName, jenkinsUserCredId, prUserCredId, prCode, useIfNotExists=false) {
  withCredentials([
    usernamePassword(credentialsId: jenkinsUserCredId, usernameVariable: 'dbUser', passwordVariable: 'PGPASSWORD'),
    string(credentialsId: host, variable: 'dbHost'),
    usernamePassword(credentialsId: prUserCredId, usernameVariable: 'ignore', passwordVariable: 'prUserPassword'),
  ]) {
    (prSchema, prUser) = Utils.generatePrNames(dbName, prCode)
    def roleExists = false

    // CREATE ROLE doesn't have a "IF NOT EXISTS" parameter so we have to check for the PR user/role manually
    if (useIfNotExists) {
      def selectRoleSqlCmd = "SELECT 1 FROM pg_roles WHERE rolname = '$prUser'"
      roleExists = Database.runPsqlCommand(this, dbHost, dbUser, dbName, selectRoleSqlCmd).contains('(1 row)')
    }

    if (roleExists) {
      echo "Role $prUser already exists, skipping"
    }
    else {
      def createRoleSqlCmd = "CREATE ROLE $prUser PASSWORD '$prUserPassword' NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT LOGIN"
      Database.runPsqlCommand(this, dbHost, dbUser, dbName, createRoleSqlCmd)
    }

    def ifNotExistsStr = useIfNotExists ? 'IF NOT EXISTS' : ''
    def createSchemaSqlCmd = "CREATE SCHEMA $ifNotExistsStr $prSchema"
    Database.runPsqlCommand(this, dbHost, dbUser, dbName, createSchemaSqlCmd)

    def grantPrivilegesSqlCmd = "GRANT ALL PRIVILEGES ON SCHEMA $prSchema TO $prUser"
    Database.runPsqlCommand(this, dbHost, dbUser, dbName, grantPrivilegesSqlCmd)

    def setSearchPathCmd = "ALTER ROLE $prUser SET search_path TO $prSchema"
    Database.runPsqlCommand(this, dbHost, dbUser, dbName, setSearchPathCmd)
  }

  return [prSchema, prUser]
}

// The design rationale for the behaviour of this function is documented here:
// https://eaflood.atlassian.net/wiki/spaces/FPS/pages/1596653973/Creating+PR+database+namespaces+in+postgres
def destroyPrDbRoleAndSchema(host, dbName, jenkinsUserCredId, prCode) {
  withCredentials([
    usernamePassword(credentialsId: jenkinsUserCredId, usernameVariable: 'dbUser', passwordVariable: 'PGPASSWORD'),
    string(credentialsId: host, variable: 'dbHost'),
  ]) {
    (prSchema, prUser) = Utils.generatePrNames(dbName, prCode)

    def dropSchemaSqlCmd = "DROP SCHEMA IF EXISTS $prSchema CASCADE"
    Database.runPsqlCommand(this, dbHost, dbUser, dbName, dropSchemaSqlCmd)

    def dropRoleSqlCmd = "DROP ROLE IF EXISTS $prUser"
    Database.runPsqlCommand(this, dbHost, dbUser, dbName, dropRoleSqlCmd)
  }
}
