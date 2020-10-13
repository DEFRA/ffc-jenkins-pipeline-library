package uk.gov.defra.ffc

import uk.gov.defra.ffc.Utils

class Provision implements Serializable {
  static String azureProvisionConfigFile = './provision.azure.yaml'

  static def createResources(ctx, environment, repoName, pr) {
    deletePrResources(ctx, environment, repoName, pr)
    createBuildAndPrQueues(ctx, environment, repoName, pr)
    createPrDatabase(ctx, environment, repoName, pr)
  }

  private static def deletePrResources(ctx, environment, repoName, pr) {
    deleteQueues(ctx, "$repoName-pr$pr-")
    deletePrDatabase(ctx, environment, repoName, pr)
  }

  private static def deleteQueues(ctx, prefix) {
    def queues = listExistingQueues(ctx, prefix)
    queues.each {
      ctx.sh("az servicebus queue delete ${getResGroupAndNamespace(ctx)} --name $it")
    }
  }

  private static def deletePrDatabase(ctx, environment, repoName, pr) {
    if (pr != '' && repoHasMigration(ctx, repoName)) {
      def migrationFolder = 'migrations'
      getMigrationFiles(ctx, migrationFolder)

      ctx.withEnv(getMigrationEnvVars(ctx, environment, repoName, pr)) {
        ctx.dir(migrationFolder) {
           // removing the schema removes the database migrations within that schema, so is unneccessary to rollback migrations
           ctx.sh("docker-compose -p $repoName-$pr -f docker-compose.migrate.yaml run schema-down")
        }
      }
    }
  }

  private static def listExistingQueues(ctx, prefix) {
    def jqCommand = "jq -r '.[]| select(.name | startswith(\"$prefix\")) | .name'"
    def script = "az servicebus queue list ${getResGroupAndNamespace(ctx)} | $jqCommand"
    def queueNames = ctx.sh(returnStdout: true, script: script).trim()
    return queueNames.tokenize('\n')
  }

  static def createBuildAndPrQueues(ctx, environment, repoName, pr) {
    if(hasResourcesToProvision(ctx, azureProvisionConfigFile)) {      
      createAllQueues(ctx, azureProvisionConfigFile, repoName, pr)
    }
  }

  static def hasResourcesToProvision(ctx, filePath) {
    return ctx.fileExists(filePath)
  }  

  static def deleteBuildResources(ctx, repoName, pr) {
    deleteQueues(ctx, getBuildQueuePrefix(ctx, repoName, pr))
  }

  private static def createAllQueues(ctx, filePath, repoName, pr) {
    def queues = readManifest(ctx, filePath, 'queues')
    createQueues(ctx, queues, repoName, pr)
  }

  private static def createQueues(ctx, queues, repoName, pr) {
    createBuildQueues(ctx, queues, repoName, pr)
    if(pr != '') {
      createPrQueues(ctx, queues, repoName, pr)
    }
  }

  private static def createPrQueues(ctx, queues, repoName, pr) {
    queues.each {
      createQueue(ctx, getPrQueueName(repoName, pr, it))
    }
  }

  static def getPrQueueName(repoName, pr, queueName) {
    return "$repoName-pr$pr-$queueName"
  }

  private static def createBuildQueues(ctx, queues, repoName, pr) {
    queues.each {
      createQueue(ctx, "${getBuildQueuePrefix(ctx, repoName, pr)}$it")
    }
  }

  static def getBuildQueueEnvVars(ctx, repoName, pr, environment) {
    // There is a single set of keys for the message queue host (as there is
    // only one). The keys are set to the `dev` env. If the PR env was to use a
    // different message queue host it would be checked here and
    // `appConfigPrefix` updated accordingly.
    def appConfigPrefix = environment + '/'
    def messageQueueHost = 'container.messageQueueHost'
    def messageQueuePassword = 'container.messageQueuePassword'
    def messageQueueUser = 'container.messageQueueUser'
    def searchKeys = [
      messageQueueHost,
      messageQueuePassword,
      messageQueueUser
    ]
    def appConfigValues = Utils.getConfigValues(ctx, searchKeys, appConfigPrefix, Utils.defaultNullLabel, false)
    def envVars = []

    if(hasResourcesToProvision(ctx, azureProvisionConfigFile)) {
      def queues = readManifest(ctx, azureProvisionConfigFile, 'queues')
      queues.each {
        envVars.push("${it.toUpperCase()}_QUEUE_ADDRESS=${getBuildQueuePrefix(ctx, repoName, pr)}$it")
      }
    }
    envVars.push("MESSAGE_QUEUE_HOST=${appConfigValues[messageQueueHost]}")
    envVars.push("MESSAGE_QUEUE_PASSWORD=${escapeQuotes(appConfigValues[messageQueuePassword])}")
    envVars.push("MESSAGE_QUEUE_USER=${appConfigValues[messageQueueUser]}")
    return envVars
  }

  private static def createQueue(ctx, queueName) {
    validateQueueName(queueName)
    def azCommand = 'az servicebus queue create'
    ctx.sh("$azCommand ${getResGroupAndNamespace(ctx)} --name $queueName --max-size 1024")
  }

  static def getProvisionedQueueConfigValues(ctx, repoName, pr) {
    def configValues = [:]

    if (hasResourcesToProvision(ctx, azureProvisionConfigFile)) {
      def queues = readManifest(ctx, azureProvisionConfigFile, 'queues')

      queues.each {
        configValues["container.${it}QueueAddress"] = getPrQueueName(repoName, pr, it)
      }
    }
    return configValues
  }  

  private static def validateQueueName(name) {
    assert name ==~ /^[A-Za-z0-9]$|^[A-Za-z0-9][\w-\.\/\~]*[A-Za-z0-9]$/ : "Invalid queue name: '$name'"
  }

  private static def escapeQuotes(value) {
    return value.replace("\"", "\\\"")
  }  

  static def readManifest(ctx, filePath, resource) {
    def resources = ctx.sh(returnStdout: true, script: "yq r $filePath resources.${resource}.*.name").trim()
    return resources.tokenize('\n')
  }

  private static def getResGroupAndNamespace (ctx) {
    return "--resource-group $ctx.AZURE_SERVICE_BUS_RESOURCE_GROUP --namespace-name $ctx.AZURE_SERVICE_BUS_NAMESPACE"
  }

  private static def getBuildQueuePrefix (ctx, repoName, pr) {
    return "$repoName-build$ctx.BUILD_NUMBER-$pr-"
  }

  private static def createPrDatabase(ctx, environment, repoName, pr) {
    if (pr != '' && ctx.fileExists('./docker-compose.migrate.yaml')) {
      def migrationFolder = 'migrations'
      getMigrationFiles(ctx, migrationFolder)

      ctx.withEnv(getMigrationEnvVars(ctx, environment, repoName, pr)) {
        ctx.dir(migrationFolder) {
          ctx.sh("docker-compose -p $repoName-$pr -f docker-compose.migrate.yaml run schema-up")
        }
        ctx.sh("docker-compose -p $repoName-$pr -f docker-compose.migrate.yaml run --no-deps database-up")
      }
    }
  }  

  private static def repoHasMigration(ctx, repoName) {
    def migrationFile = "docker-compose.migrate.yaml"
    def apiUrl = "https://api.github.com/repos/defra/$repoName/contents/$migrationFile"
    // if local file not present, check the master branch - support both PR and cleanup
    return  hasResourcesToProvision(ctx, "./$migrationFile") || Utils.getUrlStatusCode(ctx,apiUrl) == "200"
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

  public static def getSchemaName(repoName, pr) {
    if(pr != '') {
      return repoName.replace('-','_') + pr
    }
    return "public"
  }

  private static def getSchemaToken(ctx, roleName) {
    def clientId = ctx.sh(returnStdout: true, script: "az identity show --resource-group $ctx.AZURE_POSTGRES_RESOURCE_GROUP --name $roleName --query clientId --output tsv").trim()
    return ctx.sh(returnStdout: true, script: "curl -s 'http://169.254.169.254/metadata/identity/oauth2/token?api-version=2018-02-01&resource=https%3A%2F%2Fossrdbms-aad.database.windows.net&client_id=$clientId' -H Metadata:true | jq -r .access_token").trim()
  }

  private static def getCommonPostgresEnvVars(ctx, environment) {
    def appConfigPrefix = environment + '/'
    def adminUserKey = 'postgresService.ffcDemoAdminUser'
    def adminPasswordKey = 'postgresService.ffcDemoAdminPassword'
    def postgresHostKey = 'postgresService.postgresExternalName'
    def postgresUserKey = 'pr/postgresService.postgresUser'
    def searchKeys = [
      adminUserKey,
      adminPasswordKey,
      postgresHostKey,
      postgresUserKey
    ]

    def appConfigValues = Utils.getConfigValues(ctx, searchKeys, appConfigPrefix, Utils.defaultNullLabel, false)
    def schemaUser = appConfigValues[postgresUserKey]
    if (!schemaUser) {
      throw new Exception("No $postgresUserKey AppConfig in $environment environment")
    }
    def schemaRole = schemaUser.split('@')[0]
    def token = getSchemaToken(ctx, schemaRole)
    
    return [
      "POSTGRES_ADMIN_USERNAME=${appConfigValues[adminUserKey]}",
      "POSTGRES_ADMIN_PASSWORD=${escapeQuotes(appConfigValues[adminPasswordKey])}",
      "POSTGRES_HOST=${appConfigValues[postgresHostKey]}",
      "POSTGRES_SCHEMA_USERNAME=$schemaUser",
      "POSTGRES_SCHEMA_PASSWORD=$token",
      "POSTGRES_SCHEMA_ROLE=$schemaRole"
    ]
  }

  private static def getRepoPostgresEnvVars(ctx, environment, repoName, pr) {
    def appConfigPrefix = environment + '/'
    def postgresDbKey = 'postgresService.postgresDb'
    def postgresUserKey = 'postgresService.postgresUser'

    def appConfigValues = Utils.getConfigValues(ctx, [postgresDbKey, postgresUserKey], appConfigPrefix, repoName, false)

    def database = appConfigValues[postgresDbKey]
    if (!database) {
      throw new Exception("No $postgresDbKey AppConfig for $repoName in $environment environment")
    }

    def schemaName = getSchemaName(repoName, pr)

    // if not PR then need to get correct managed identiy and not PR identity
    if(pr != '') {
      def schemaUser = appConfigValues[postgresUserKey]
      if (!schemaUser) {
        throw new Exception("No $postgresUserKey AppConfig in $environment environment")
      }
      def schemaRole = schemaUser.split('@')[0]
      def token = getSchemaToken(ctx, schemaRole)
      return [
        "POSTGRES_DB=$database",
        "POSTGRES_SCHEMA_NAME=$schemaName",
        "POSTGRES_SCHEMA_USERNAME=$schemaUser",
        "POSTGRES_SCHEMA_PASSWORD=$token",
      ]
    } else {
      return [
        "POSTGRES_DB=$database",
        "POSTGRES_SCHEMA_NAME=$schemaName",
      ]
    }
  }

  static def getProvisionedDbSchemaConfigValues(ctx, repoName, pr) {
    def configValues = [:]
    if (ctx.fileExists( './docker-compose.migrate.yaml')) {
       configValues['postgresService.postgresSchema'] = getSchemaName(repoName, pr)
    }
    return configValues
  }

  static def getMigrationEnvVars(ctx, environment, repoName, pr) {
    def envVars = getCommonPostgresEnvVars(ctx, environment)
    def repoEnvVars = getRepoPostgresEnvVars(ctx, environment, repoName, pr)
    return envVars + repoEnvVars
  }  
}
