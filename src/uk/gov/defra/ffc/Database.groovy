package uk.gov.defra.ffc

class Database implements Serializable {
  static def runPsqlCommand(ctx, dbHost, dbUser, dbName, sqlCmd) {
    ctx.sh(returnStdout: true, script: "psql --host=$dbHost --username=$dbUser --dbname=$dbName --no-password --command=\"$sqlCmd;\"")
  }
}
