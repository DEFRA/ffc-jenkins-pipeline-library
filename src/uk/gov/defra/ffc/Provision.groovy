package uk.gov.defra.ffc

class Provision implements Serializable {
  static def createResources(ctx, environment, repoName, pr) {
    createAzureResources(ctx, repoName, pr)
    createPrDatabase(ctx, environment, repoName, pr)
  }

  static def createAzureResources(ctx, repoName, pr) {
    def filePath = 'provision.azure.yaml'
    if(ctx.fileExists(filePath)) {
      deletePrResources(ctx, repoName, pr)
      createAllResources(ctx, filePath, repoName, pr)
    }
  }

  static def deleteBuildResources(ctx, repoName, pr) {
    deleteQueues(ctx, getBuildQueuePrefix(ctx, repoName, pr))
  }

  private static def createAllResources(ctx, filePath, repoName, pr) {
    def queues = readManifest(ctx, filePath, 'queues')
    createQueues(ctx, queues, repoName, pr)
  }

  private static def createQueues(ctx, queues, repoName, pr) {
    createBuildQueues(ctx, queues, repoName, pr)
    if(pr != '') {
      createPrQueues(ctx, queues, repoName, pr)
    }
  }

  private static def createPrDatabase(ctx, environment, repoName, pr) {
    if (pr != '' && ctx.fileExists('./docker-compose.migrate.yaml')) {
      def migrationFolder = 'migrations'
      getMigrationFiles(ctx, migrationFolder)

      ctx.withEnv(getMigrationEnvVars(ctx, environment, repoName, pr)) {
        ctx.dir(migrationFolder) {
          // migrations may change in different builds of a PR, refresh schema to avoid errors
          ctx.sh("docker-compose -p $repoName-$pr -f docker-compose.migrate.yaml run schema-down")
          ctx.sh("docker-compose -p $repoName-$pr -f docker-compose.migrate.yaml run schema-up")
        }
        ctx.sh("docker-compose -p $repoName-$pr -f docker-compose.migrate.yaml run --no-deps database-up")
      }
    }
  }

  private static def deletePrDatabase(ctx, environment, repoName, pr) {
    if (pr != '' && repoHasMigration(ctx, repoName)) {
      def migrationFolder = 'migrations'
      getMigrationFiles(ctx, migrationFolder)

      ctx.withEnv(getMigrationEnvVars(ctx, environment, repoName, pr)) {
        ctx.dir(migrationFolder) {
           // removing the schema removes the database migrations within that schema, so is unneccessary
           ctx.sh("docker-compose -p $repoName-$pr -f docker-compose.migrate.yaml run schema-down")
        }
      }
    }
  }

  private static def repoHasMigration(ctx, repoName) {
    def apiUrl = "https://api.github.com/repos/defra/$repoName/contents/docker-compose.migrate.yaml"
    return Utils.getUrlStatusCode(ctx,apiUrl) == "200"
  }


  private static def getMigrationFiles(ctx, destinationFolder){
    def resourcePath = 'uk/gov/defra/ffc/migration'
    ctx.sh("rm -rf $destinationFolder")
    getResourceScript(ctx, "$resourcePath/scripts", 'schema-up', "$destinationFolder/scripts")
    getResourceScript(ctx, "$resourcePath/scripts", 'schema-down', "$destinationFolder/scripts")
    getResourceFile(ctx, resourcePath, 'docker-compose.migrate.yaml', destinationFolder)
    getResourceFile(ctx, "$resourcePath/changelog", 'schema.changelog.xml', "$destinationFolder/changelog")
  }

  private static def getResourceFile(ctx, resourcePath, filename, destinationFolder, makeExecutable = false){
    def fileContent = ctx.libraryResource("$resourcePath/$filename")
    ctx.writeFile(file: "$destinationFolder/$filename", text: fileContent, encoding: "UTF-8")
    if (makeExecutable) {
      ctx.sh("chmod 777 ./$destinationFolder/$filename")
    }
    ctx.echo "written $filename to $destinationFolder"
  }

  private static def getResourceScript(ctx, resourcePath, filename, destinationFolder){
    getResourceFile(ctx, resourcePath, filename, destinationFolder, true)
  }

  private static def getMigrationEnvVars(ctx, environment, repoName, pr) {
    def searchKeys = [
      'POSTGRES_ADMIN_USERNAME',
      'POSTGRES_ADMIN_PASSWORD',
      'POSTGRES_HOST',
      'POSTGRES_SCHEMA_PASSWORD'
    ]
    def appConfigPrefix = environment + '/'
    def appConfigValues = Utils.getConfigValues(ctx, searchKeys, appConfigPrefix, Utils.defaultNullLabel, false)
    appConfigValues['POSTGRES_ADMIN_PASSWORD'] = escapeQuotes(appConfigValues['POSTGRES_ADMIN_PASSWORD'])
    appConfigValues['POSTGRES_SCHEMA_PASSWORD'] = escapeQuotes(appConfigValues['POSTGRES_SCHEMA_PASSWORD'])

    def migrationEnvVars = appConfigValues.collect { "$it.key=$it.value" }

    def schemaName = repoName.replace('-','_') + pr
    def schemaRole = "${schemaName}_role"
    def schemaUser = getSchemaUserWithHostname(schemaRole, appConfigValues['POSTGRES_HOST'])
    def databaseName = repoName.replace('-','_').replace('_service', '')

    migrationEnvVars.add("POSTGRES_SCHEMA_ROLE=$schemaRole")
    migrationEnvVars.add("POSTGRES_SCHEMA_USERNAME=$schemaUser")
    migrationEnvVars.add("POSTGRES_SCHEMA_NAME=$schemaName")
    migrationEnvVars.add("POSTGRES_DB=$databaseName")

    return migrationEnvVars
  }

  private static def escapeQuotes(value) {
    return value.replace("\"", "\\\"")
  }

  private static getSchemaUserWithHostname(schemaRole, dbServer) {
    // string includes quotes, so remove them and escape '.' as split takes a regex
    def dbServerSplit = dbServer.replace("\"","").split('\\.')
    return dbServerSplit.length > 1 ? "${schemaRole}@${dbServerSplit[0]}" : schemaRole
  }

  private static def createPrQueues(ctx, queues, repoName, pr) {
    queues.each {
      createQueue(ctx, "$repoName-pr$pr-$it")
    }
  }

  private static def createBuildQueues(ctx, queues, repoName, pr) {
    queues.each {
      createQueue(ctx, "${getBuildQueuePrefix(ctx, repoName, pr)}$it")
    }
  }

  private static def createQueue(ctx, queueName) {
    validateQueueName(queueName)  
    def azCommand = 'az servicebus queue create'
    ctx.sh("$azCommand ${getResGroupAndNamespace(ctx)} --name $queueName --max-size 1024")
  }

  private static def deletePrResources(ctx, environment, repoName, pr) {
    deleteQueues(ctx, "$repoName-pr$pr-")
    deletePrDatabase(ctx, environment, repoName, pr)
  }

  private static def deleteQueues(ctx, prefix) {
    def queues = listQueues(ctx, prefix)
    queues.each {
      ctx.sh("az servicebus queue delete ${getResGroupAndNamespace(ctx)} --name $it")
    }
  }

  private static def validateQueueName(name) {
    assert name ==~ /^[A-Za-z0-9]$|^[A-Za-z0-9][\w-\.\/\~]*[A-Za-z0-9]$/ : "Invalid queue name: '$name'"
  }

  private static def readManifest(ctx, filePath, resource) {
    def resources = ctx.sh(returnStdout: true, script: "yq r $filePath resources.${resource}.*.name").trim()
    return resources.tokenize('\n')
  }

  private static def getResGroupAndNamespace (ctx) {
    return "--resource-group $ctx.AZURE_SERVICE_BUS_RESOURCE_GROUP --namespace-name $ctx.AZURE_SERVICE_BUS_NAMESPACE"
  }

  private static def getBuildQueuePrefix (ctx, repoName, pr) {
    return "$repoName-build$ctx.BUILD_NUMBER-$pr-"
  }

  private static def listQueues(ctx, prefix) {
    def jqCommand = "jq -r '.[]| select(.name | startswith(\"$prefix\")) | .name'"
    def script = "az servicebus queue list ${getResGroupAndNamespace(ctx)} | $jqCommand"
    def queueNames = ctx.sh(returnStdout: true, script: script).trim()
    return queueNames.tokenize('\n')
  }
}
