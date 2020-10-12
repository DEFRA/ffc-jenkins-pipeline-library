package uk.gov.defra.ffc

class Database implements Serializable {
  private static def runPsqlCommand(ctx, dbHost, dbUser, dbName, sqlCmd) {
    ctx.sh(returnStdout: true, script: "psql --host=$dbHost --username=$dbUser --dbname=$dbName --no-password --command=\"$sqlCmd;\"")
  }

  // The design rationale for the behaviour of this function is documented here:
  // https://eaflood.atlassian.net/wiki/spaces/FPS/pages/1596653973/Creating+PR+database+namespaces+in+postgres
  static def provisionPrDbRoleAndSchema(ctx, host, dbName, jenkinsUserCredId, prUserCredId, prCode, useIfNotExists=false) {
    ctx.withCredentials([
      ctx.usernamePassword(credentialsId: jenkinsUserCredId, usernameVariable: 'dbUser', passwordVariable: 'PGPASSWORD'),
      ctx.string(credentialsId: host, variable: 'dbHost'),
      ctx.usernamePassword(credentialsId: prUserCredId, usernameVariable: 'ignore', passwordVariable: 'prUserPassword'),
    ]) {
      def prSchema = ''
      def prUser = ''
      (prSchema, prUser) = Utils.generatePrNames(dbName, prCode)
      def roleExists = false

      // CREATE ROLE doesn't have a "IF NOT EXISTS" parameter so we have to check for the PR user/role manually
      if (useIfNotExists) {
        def selectRoleSqlCmd = "SELECT 1 FROM pg_roles WHERE rolname = '$prUser'"
        roleExists = Database.runPsqlCommand(ctx, ctx.dbHost, ctx.dbUser, dbName, selectRoleSqlCmd).contains('(1 row)')
      }

      if (roleExists) {
        ctx.echo("Role $prUser already exists, skipping")
      } else {
        def createRoleSqlCmd = "CREATE ROLE $prUser PASSWORD '$ctx.prUserPassword' NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT LOGIN"
        Database.runPsqlCommand(ctx, ctx.dbHost, ctx.dbUser, dbName, createRoleSqlCmd)
      }

      def ifNotExistsStr = useIfNotExists ? 'IF NOT EXISTS' : ''
      def createSchemaSqlCmd = "CREATE SCHEMA $ifNotExistsStr $prSchema"
      Database.runPsqlCommand(ctx, ctx.dbHost, ctx.dbUser, dbName, createSchemaSqlCmd)

      def grantPrivilegesSqlCmd = "GRANT ALL PRIVILEGES ON SCHEMA $prSchema TO $prUser"
      Database.runPsqlCommand(ctx, ctx.dbHost, ctx.dbUser, dbName, grantPrivilegesSqlCmd)

      def setSearchPathCmd = "ALTER ROLE $prUser SET search_path TO $prSchema"
      Database.runPsqlCommand(ctx, ctx.dbHost, ctx.dbUser, dbName, setSearchPathCmd)

      return [prSchema, prUser]
    }
  }

  // The design rationale for the behaviour of this function is documented here:
  // https://eaflood.atlassian.net/wiki/spaces/FPS/pages/1596653973/Creating+PR+database+namespaces+in+postgres
  static def destroyPrDbRoleAndSchema(ctx, host, dbName, jenkinsUserCredId, prCode) {
    ctx.withCredentials([
      ctx.usernamePassword(credentialsId: jenkinsUserCredId, usernameVariable: 'dbUser', passwordVariable: 'PGPASSWORD'),
      ctx.string(credentialsId: host, variable: 'dbHost'),
    ]) {
      def prSchema = ''
      def prUser = ''
      (prSchema, prUser) = Utils.generatePrNames(dbName, prCode)

      def dropSchemaSqlCmd = "DROP SCHEMA IF EXISTS $prSchema CASCADE"
      Database.runPsqlCommand(ctx, ctx.dbHost, ctx.dbUser, dbName, dropSchemaSqlCmd)

      def dropRoleSqlCmd = "DROP ROLE IF EXISTS $prUser"
      Database.runPsqlCommand(ctx, ctx.dbHost, ctx.dbUser, dbName, dropRoleSqlCmd)
    }
  }

  static runRemoteMigrations(ctx, environment, repoName, version) {    
    ctx.sh("wget https://api.github.com/repos/defra/${repoName}/tarball/${version} -O release")
    ctx.sh("tar -xvf release")
    def workingFolder = ctx.sh(returnStdout: true, script: "ls | grep DEFRA-${repoName}").trim()
    ctx.dir(workingFolder)
      ctx.sh("ls -la") {
      if(ctx.fileExists("changelog")) {
        ctx.echo("has migrations")
      } else {
        ctx.echo("has no migrations")
      }
    }
  }
}
