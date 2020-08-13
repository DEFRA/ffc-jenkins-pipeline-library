package uk.gov.defra.ffc

class Provision implements Serializable {
  static def createResources(ctx, environment, repoName, pr) {
    def filePath = 'provision.azure.yaml'
    if(ctx.fileExists(filePath)) {
      deletePrResources(ctx, repoName, pr)
      createAllResources(ctx, filePath, repoName, pr)
    }
    createPrDatabase(ctx, environment, repoName, pr)
  }

  static def deleteBuildResources(ctx, environment, repoName, pr) {
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
      def envVars = getMigrationEnvVars(ctx, environment, repoName, pr)
      def migrationFolder = 'migrations'
      getMigrationFiles(ctx, migrationFolder)
      ctx.dir(migrationFolder) {
        ctx.sh("$Utils.suppressConsoleOutput $envVars docker-compose -p $repoName-$pr -f docker-compose.migrate.yaml run schema-up")
      }
      ctx.sh("$Utils.suppressConsoleOutput $envVars docker-compose -p $repoName-$pr -f docker-compose.migrate.yaml run database-up")
    }
  }

  private static def deletePrDatabase(ctx, environment, repoName, pr) {
    if (pr != '' && ctx.fileExists('./docker-compose.migrate.yaml')) {
      def envVars = getMigrationEnvVars(ctx, environment, repoName, pr)
      def migrationFolder = 'migrations'
      getMigrationFiles(ctx, migrationFolder)
      // removing the schema removes the database migrations within that schema, so is unneccessary
      ctx.dir(migrationFolder) {
        ctx.sh("$Utils.suppressConsoleOutput $envVars docker-compose -p $repoName-$pr -f docker-compose.migrate.yaml run schema-down")
      }
    }
  }

  private static def getMigrationFiles(ctx, destinationFolder){
    def resourcePath = 'uk/gov/defra/ffc/migration'
    ctx.sh(" rm -rf $destinationFolder")
    getResourceScript(ctx, resourcePath, 'schema-up', "$destinationFolder/scripts")
    getResourceScript(ctx, resourcePath, 'schema-down', "$destinationFolder/scripts")
    getResourceFile(ctx, resourcePath, 'docker-compose.migrate.yaml', destinationFolder)
    getResourceFile(ctx, resourcePath, 'schema.changelog.xml', "$destinationFolder/changelog")
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
      'POSTGRES_HOST',
      'POSTGRES_ADMIN_USERNAME',
      'POSTGRES_ADMIN_PASSWORD',
      'POSTGRE_SCHEMA_PASSWORD'
    ]
    def appConfigPrefix = environment + '/'
    def appConfigValues = Helm.getConfigValues(ctx, searchKeys, appConfigPrefix)

    def appConfigEnvs = appConfigValues.collect { "$it.key=$it.value" }.join(' ')

    def schemaName = repoName.replace('-','_') + pr
    def schemaRole = repoName.replace('-','_') + pr + "role"
    def schemaUser = getSchemaUserWithHostname(schemaRole, appConfigValues['postgresService.postgresExternalName'])
    def databaseName = repoName.replace('-','_').replace('_service', '')

    def prEnvs = "SCHEMA_ROLE=$schemaRole SCHEMA_USERNAME=$schemaUser SCHEMA_NAME=$schemaName POSTGRES_DB=$databaseName"
    return "$appConfigEnvs $prEnvs"
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

  private static def deletePrResources(ctx, repoName, pr) {
    deleteQueues(ctx, "$repoName-pr$pr-")
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
