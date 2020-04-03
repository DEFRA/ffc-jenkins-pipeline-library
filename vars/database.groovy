// private
def generatePrNames(dbName, prCode) {
  def prSchema = "pr$prCode"
  def prUser = "${dbName}_$prSchema"
  return [prSchema, prUser]
}

// private
def runPsqlCommand(dbHost, dbUser, dbName, sqlCmd) {
  sh returnStdout: true, script: "psql --host=$dbHost --username=$dbUser --dbname=$dbName --no-password --command=\"$sqlCmd;\""
}

// The design rationale for the behaviour of this function is documented here:
// https://eaflood.atlassian.net/wiki/spaces/FPS/pages/1596653973/Creating+PR+database+namespaces+in+postgres
// public
def provisionPrDatabaseRoleAndSchema(host, dbName, jenkinsUserCredId, prUserCredId, prCode, useIfNotExists=false) {
  withCredentials([
    usernamePassword(credentialsId: jenkinsUserCredId, usernameVariable: 'dbUser', passwordVariable: 'PGPASSWORD'),
    string(credentialsId: host, variable: 'dbHost'),
    usernamePassword(credentialsId: prUserCredId, usernameVariable: 'ignore', passwordVariable: 'prUserPassword'),
  ]) {
    (prSchema, prUser) = generatePrNames(dbName, prCode)
    def roleExists = false

    // CREATE ROLE doesn't have a "IF NOT EXISTS" parameter so we have to check for the PR user/role manually
    if (useIfNotExists) {
      def selectRoleSqlCmd = "SELECT 1 FROM pg_roles WHERE rolname = '$prUser'"
      roleExists = runPsqlCommand(dbHost, dbUser, dbName, selectRoleSqlCmd).contains("(1 row)")
    }

    if (roleExists) {
      echo "Role $prUser already exists, skipping"
    }
    else {
      def createRoleSqlCmd = "CREATE ROLE $prUser PASSWORD '$prUserPassword' NOSUPERUSER NOCREATEDB CREATEROLE INHERIT LOGIN"
      runPsqlCommand(dbHost, dbUser, dbName, createRoleSqlCmd)
    }

    def ifNotExistsStr = useIfNotExists ? "IF NOT EXISTS" : ""
    def createSchemaSqlCmd = "CREATE SCHEMA $ifNotExistsStr $prSchema"
    runPsqlCommand(dbHost, dbUser, dbName, createSchemaSqlCmd)

    def grantPrivilegesSqlCmd = "GRANT ALL PRIVILEGES ON SCHEMA $prSchema TO $prUser"
    runPsqlCommand(dbHost, dbUser, dbName, grantPrivilegesSqlCmd)
  }

  return generatePrNames(dbName, prCode)
}

// The design rationale for the behaviour of this function is documented here:
// https://eaflood.atlassian.net/wiki/spaces/FPS/pages/1596653973/Creating+PR+database+namespaces+in+postgres
// public
def destroyPrDatabaseRoleAndSchema(host, dbName, jenkinsUserCredId, prCode) {
  withCredentials([
    usernamePassword(credentialsId: jenkinsUserCredId, usernameVariable: 'dbUser', passwordVariable: 'PGPASSWORD'),
    string(credentialsId: host, variable: 'dbHost'),
  ]) {
    (prSchema, prUser) = generatePrNames(dbName, prCode)

    def dropSchemaSqlCmd = "DROP SCHEMA IF EXISTS $prSchema CASCADE"
    runPsqlCommand(dbHost, dbUser, dbName, dropSchemaSqlCmd)

    def dropRoleSqlCmd = "DROP ROLE IF EXISTS $prUser"
    runPsqlCommand(dbHost, dbUser, dbName, dropRoleSqlCmd)
  }
}
