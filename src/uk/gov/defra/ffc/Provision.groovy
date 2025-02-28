package uk.gov.defra.ffc

import uk.gov.defra.ffc.Utils

class Provision implements Serializable {

  static final String azureProvisionConfigFile = './provision.azure.yaml'
  static final String identityPrefix = 'SNDFFCINFMID2001'
  static final String subscriptionSND2 = 'dc785a20-057f-4023-b51f-f23a00f1ca2e' // AZD-FFC-SND2
  static final String subscriptionSND1 = 'cd4e9a00-99d8-45a2-98bb-7648ef12c26d' // AZD-FFC-SND1
  static final String defraCloudDevTenant = 'c9d74090-b4e6-4b04-981d-e6757a160812'

  // Public Methods

  static def createResources(ctx, environment, repoName, tag, pr) {
    init(ctx)
    createManagedIdentity(ctx)
    createDatabase(ctx)
    createDatabaseUser(ctx)
    addDatabaseExtensions(ctx)
    deletePrResources(ctx, environment, repoName, pr)
    createPrDatabase(ctx, environment, repoName, pr)
    grantDbPrivileges(ctx, repoName, pr)
    createServiceBusEntities(ctx, environment, repoName, pr)
    createPrIdentityFederation(ctx, environment, repoName, tag, pr)
  }

  static def deletePrResources(ctx, environment, repoName, pr) {
    init(ctx, true, repoName)
    String smalRepoName = repoName
    if (Utils.repoNames.containsKey(repoName.replaceAll('-', ''))) {
      smalRepoName = Utils.repoNames[repoName.replaceAll('-', '')]
    }
    deleteServiceBusEntities(ctx, "$smalRepoName-pr$pr-", 'queue')
    deleteServiceBusEntities(ctx, "$smalRepoName-pr$pr-", 'topic')
    deletePrDatabase(ctx, environment, repoName, pr)
    deletePrIdentityFederation(ctx, environment, repoName, pr)
  }

  static def deleteBuildResources(ctx, repoName, pr) {
    init(ctx)
    deleteServiceBusEntities(ctx, getBuildQueuePrefix(ctx, repoName, pr), 'queue')
    deleteServiceBusEntities(ctx, getBuildQueuePrefix(ctx, repoName, pr), 'topic')
  }

  static def getMigrationEnvVars(ctx, environment, repoName, pr) {
    init(ctx)
    def envVars = getPostgresAdminEnvVars(ctx)
    def repoEnvVars = getRepoPostgresEnvVars(ctx, environment, repoName, pr)
    def userEnvVars = getUserPostgresEnvVars(ctx, environment, repoName, pr)
    return envVars + repoEnvVars + userEnvVars
  }

  static def getProvisionedQueueConfigValues(ctx, repoName, pr) {
    init(ctx)
    def queueConfigValues = [:]
    def topicConfigValues = [:]

    if (hasResourcesToProvision(ctx, azureProvisionConfigFile)) {
      def queues = readManifest(ctx, azureProvisionConfigFile, 'queues')
      queues.each {
        queueConfigValues["container.${it}QueueAddress"] = getPrQueueName(repoName, pr, it)
      }
      def topics = readManifest(ctx, azureProvisionConfigFile, 'topics')
      topics.each {
        topicConfigValues["container.${it}TopicAddress"] = getPrQueueName(repoName, pr, it)
        topicConfigValues["container.${it}SubscriptionAddress"] = getPrQueueName(repoName, pr, it)
      }
    }
    return queueConfigValues + topicConfigValues
  }

  static def getProvisionedDbSchemaConfigValues(ctx, repoName, pr) {
    init(ctx)
    def configValues = [:]
    if (ctx.fileExists('./docker-compose.migrate.yaml')) {
      configValues['postgresService.postgresSchema'] = getSchemaName(repoName, pr)
    }
    return configValues
  }

  // Private Methods

  private static def init(ctx, checkoutIfNeeded=false, repoName="") {
    // Check if the provition file is exist
    if (!hasResourcesToProvision(ctx, azureProvisionConfigFile)) {
      if (checkoutIfNeeded) {
        ctx.withCredentials([
          ctx.string(credentialsId: 'github-auth-token', variable: 'gitToken')
        ]) {
          ctx.sh("curl -O -H 'Authorization: token $ctx.gitToken' https://raw.githubusercontent.com/DEFRA/$repoName/refs/heads/main/provision.azure.yaml ")
        }
        if (!hasResourcesToProvision(ctx, azureProvisionConfigFile)) {
          ctx.echo("The ${azureProvisionConfigFile} file is not exist!")
          throw new Exception("The ${azureProvisionConfigFile} file is not exist!")
        }
      } else {
        ctx.echo("The ${azureProvisionConfigFile} file is not exist!")
        throw new Exception("The ${azureProvisionConfigFile} file is not exist!")
      }
    }
    ctx.sh('mkdir -p jenkins-azure/.azure')
    ctx.withCredentials([ctx.azureServicePrincipal('SSVFFCJENSR1001-Jenkins')]) {
      Utils.runAzCommand(ctx, "az login --service-principal -u $ctx.AZURE_CLIENT_ID -p $ctx.AZURE_CLIENT_SECRET -t $defraCloudDevTenant")
      Utils.runAzCommand(ctx, "az account set --subscription ${subscriptionSND2}")
    }
  }

  private static def deleteServiceBusEntities(ctx, prefix, entity) {
    def entities = listExistingServiceBusEntities(ctx, prefix, entity)
    entities.each {
      deleteGrantedBusPrivileges(ctx, entity, it)
      Utils.runAzCommand(ctx, "az servicebus ${entity} delete ${getResGroupAndNamespace(ctx)} --name $it")
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

  private static def listExistingServiceBusEntities(ctx, prefix, entity) {
    def jqCommand = "jq -r '.[]| select(.name | startswith(\"$prefix\")) | .name'"
    def script = "az servicebus ${entity} list ${getResGroupAndNamespace(ctx)} | $jqCommand"
    def queueNames = Utils.runAzCommand(ctx, script).trim()
    // def queueNames = ctx.sh(returnStdout: true, script: script).trim()
    return queueNames.tokenize('\n')
  }

  private static def hasResourcesToProvision(ctx, filePath) {
    return ctx.fileExists(filePath)
  }

  // Service Bus

  private static def createServiceBusEntities(ctx, environment, repoName, pr) {
    if (hasResourcesToProvision(ctx, azureProvisionConfigFile)) {
      createAllServiceBusEntities(ctx, azureProvisionConfigFile, repoName, pr)
    }
  }

  private static def createAllServiceBusEntities(ctx, filePath, repoName, pr) {
    def queues = readManifest(ctx, filePath, 'queues')
    createQueues(ctx, queues, repoName, pr)
    def topics = readManifest(ctx, filePath, 'topics')
    createTopics(ctx, topics, repoName, pr)
  }

  private static def createQueues(ctx, queues, repoName, pr) {
    createBuildQueues(ctx, queues, repoName, pr)
    if (pr != '') {
      createPrQueues(ctx, queues, repoName, pr)
    }
  }

  private static def createTopics(ctx, topics, repoName, pr) {
    createBuildTopics(ctx, topics, repoName, pr)
    if (pr != '') {
      createPrTopics(ctx, topics, repoName, pr)
    }
  }

  private static def createBuildQueues(ctx, queues, repoName, pr) {
    queues.each {
      String sessionOption = getSessionOption(ctx, azureProvisionConfigFile, 'queues', it)
      createQueue(ctx, "${getBuildQueuePrefix(ctx, repoName, pr)}$it", sessionOption)
    }
  }

  private static def createBuildTopics(ctx, topics, repoName, pr) {
    topics.each {
      createTopicAndSubscription(ctx, "${getBuildQueuePrefix(ctx, repoName, pr)}$it", it)
    }
  }

  private static def createPrQueues(ctx, queues, repoName, pr) {
    queues.each {
      String sessionOption = getSessionOption(ctx, azureProvisionConfigFile, 'queues', it)
      createQueue(ctx, getPrQueueName(repoName, pr, it), sessionOption)
    }
  }

  private static def createPrTopics(ctx, topics, repoName, pr) {
    topics.each {
      createTopicAndSubscription(ctx, getPrQueueName(repoName, pr, it), it)
    }
  }

  private static def createQueue(ctx, queueName, sessionOption = '') {
    String trimedQueueName = queueName.take(50)
    if (trimedQueueName != null && trimedQueueName.length() > 0 && trimedQueueName.charAt(trimedQueueName.length() - 1) == '-') {
      trimedQueueName = trimedQueueName.substring(0, trimedQueueName.length() - 1)
    }
    validateQueueName(trimedQueueName)
    String azCommand = 'az servicebus queue create'
    Utils.runAzCommand(ctx, "$azCommand ${getResGroupAndNamespace(ctx)} --name $trimedQueueName --max-size 1024 $sessionOption")
    grantBusPrivileges(ctx, 'queue', trimedQueueName)
  }

  static def getSessionOption(ctx, filePath, resource, name) {
    String script = "yq r $filePath resources.${resource}*\\(name==${name}\\).enableSessions"
    ctx.echo(script)
    def option = ctx.sh(returnStdout: true, script: script).trim()
    return option.toBoolean() ? '--enable-session' : ''
  }

  private static def createTopicAndSubscription(ctx, topicName, originalTopicName) {
    String trimedTopicName = topicName.take(50)
    if (trimedTopicName != null && trimedTopicName.length() > 0 && trimedTopicName.charAt(trimedTopicName.length() - 1) == '-') {
      trimedTopicName = trimedTopicName.substring(0, trimedTopicName.length() - 1)
    }
    validateQueueName(trimedTopicName)
    def azTopicCommand = 'az servicebus topic create'
    Utils.runAzCommand(ctx, "$azTopicCommand ${getResGroupAndNamespace(ctx)} --name $trimedTopicName --max-size 1024")
    grantBusPrivileges(ctx, 'topic', trimedTopicName)

    def azSubscriptionCommand = 'az servicebus topic subscription create'
    def subNames = readSubscriptionsFromManifest(ctx, azureProvisionConfigFile, originalTopicName)
    ctx.echo("Loop on Subscriptions: ${subNames}")
    subNames.each {
      Utils.runAzCommand(ctx, "$azSubscriptionCommand ${getResGroupAndNamespace(ctx)} --name $it --topic-name $trimedTopicName")
    }
  }

  static def getPrQueueName(repoName, pr, queueName) {
    String smalRepoName = repoName
    if (Utils.repoNames.containsKey(repoName.replaceAll('-', ''))) {
      smalRepoName = Utils.repoNames[repoName.replaceAll('-', '')]
    }
    return "$smalRepoName-pr$pr-$queueName"
  }

  private static def getMessageQueueCreds(ctx) {
    def messageQueueHost = "${ctx.AZURE_SERVICE_BUS_NAMESPACE_SND2}.servicebus.windows.net"
    def messageQueuePassword = Utils.runAzCommand(ctx,  "az servicebus namespace authorization-rule keys list --resource-group ${ctx.AZURE_SERVICE_BUS_RESOURCE_GROUP_SND2} --namespace-name ${ctx.AZURE_SERVICE_BUS_NAMESPACE_SND2} --name RootManageSharedAccessKey --query primaryConnectionString --output tsv")
    def messageQueueUser = 'RootManageSharedAccessKey'

    def envVars = []
    envVars.push("MESSAGE_QUEUE_HOST=${messageQueueHost}")
    envVars.push("MESSAGE_QUEUE_PASSWORD=${messageQueuePassword}")
    envVars.push("MESSAGE_QUEUE_USER=${messageQueueUser}")
    return envVars
  }

  static def getBuildQueueEnvVars(ctx, repoName, pr) {
    return getQueueEnvVars(ctx, repoName, pr, 'build')
  }

  static def getPrQueueEnvVars(ctx, repoName, pr) {
    return getQueueEnvVars(ctx, repoName, pr, 'pr')
  }

  private static def getQueueEnvVars(ctx, repoName, pr, queueType) {
    def envVars = getMessageQueueCreds(ctx)

    if (hasResourcesToProvision(ctx, azureProvisionConfigFile)) {
      def queues = readManifest(ctx, azureProvisionConfigFile, 'queues')
      queues.each {
        if (queueType == 'pr') {
          envVars.push("${it.toUpperCase()}_QUEUE_ADDRESS=${getPrQueueName(repoName, pr, it)}")
        } else if (queueType == 'build') {
          envVars.push("${it.toUpperCase()}_QUEUE_ADDRESS=${getBuildQueuePrefix(ctx, repoName, pr)}$it")
        }
      }
      def topics = readManifest(ctx, azureProvisionConfigFile, 'topics')
      topics.each {
        if (queueType == 'pr') {
          envVars.push("${it.toUpperCase()}_TOPIC_ADDRESS=${getPrQueueName(repoName, pr, it)}")
          envVars.push("${it.toUpperCase()}_SUBSCRIPTION_ADDRESS=${getPrQueueName(repoName, pr, it)}")
        } else if (queueType == 'build') {
          envVars.push("${it.toUpperCase()}_TOPIC_ADDRESS=${getBuildQueuePrefix(ctx, repoName, pr)}$it")
          envVars.push("${it.toUpperCase()}_SUBSCRIPTION_ADDRESS=${getBuildQueuePrefix(ctx, repoName, pr)}$it")
        }
      }
    }
    return envVars
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

  static def readSubscriptionsFromManifest(ctx, filePath, topicName) {
    def provisionFile = ctx.readYaml file: filePath
    def subs = []
    provisionFile["resources"]["topics"].each {
      ctx.echo("topic name: ${topicName}")
      ctx.echo("provision file topic name: ${it['name']}")
      if (it["name"] == topicName) {
        ctx.echo("Subscriptions: ${it['subscriptions']}")
        if (it["subscriptions"] != null) {
          subs = it["subscriptions"]*.name
          ctx.echo("Subscription list: ${subs}")
          return subs
        }
      }
    }
    return subs
  }

  private static def readManifestSingle(ctx, filePath, resource) {
    def resources = ctx.sh(returnStdout: true, script: "yq r $filePath resources.${resource}").trim()
    return resources.tokenize('\n')[0]
  }

  private static def getResGroupAndNamespace(ctx) {
    return "--resource-group $ctx.AZURE_SERVICE_BUS_RESOURCE_GROUP_SND2 --namespace-name $ctx.AZURE_SERVICE_BUS_NAMESPACE_SND2"
  }

  private static def getBuildQueuePrefix(ctx, repoName, pr) {
    String smalRepoName = repoName
    if (Utils.repoNames.containsKey(repoName.replaceAll('-', ''))) {
      smalRepoName = Utils.repoNames[repoName.replaceAll('-', '')]
    }
    return "$smalRepoName-b$ctx.BUILD_NUMBER-$pr-"
  }

  private static def createPrIdentityFederation(ctx, environment, repoName, tag, pr) {
    if (pr != '') {
      def federatedName = "${repoName}-${pr}"
      String identity = readManifestSingle(ctx, azureProvisionConfigFile, 'identity')
      def identityName = "${identityPrefix}-${identity}"
      def oidcIssuer = 'https://northeurope.oic.prod-aks.azure.com/c9d74090-b4e6-4b04-981d-e6757a160812/253ef0ad-df48-4058-8b21-b2e0b1860f54/'
      def subject = "system:serviceaccount:$repoName-$tag:$repoName"
      def audience = 'api://AzureADTokenExchange'
      Utils.runAzCommand(ctx,  "az identity federated-credential create --name ${federatedName} --identity-name ${identityName} --resource-group ${ctx.AZURE_POSTGRES_RESOURCE_GROUP_SND2} --issuer ${oidcIssuer} --subject ${subject} --audience ${audience}")
    }
  }

  private static def deletePrIdentityFederation(ctx, environment, repoName, pr) {
    if (pr != '') {
      def federatedName = "${repoName}-${pr}"
      String identity = readManifestSingle(ctx, azureProvisionConfigFile, 'identity')
      def identityName = "${identityPrefix}-${identity}"
      Utils.runAzCommand(ctx, "az identity federated-credential delete --name ${federatedName} --identity-name ${identityName} --resource-group ${ctx.AZURE_POSTGRES_RESOURCE_GROUP_SND2} --yes")
    }
  }

  // Identity Operations
  private static def createManagedIdentity(ctx) {
    String identity = readManifestSingle(ctx, azureProvisionConfigFile, 'identity')
    def identityName = "${identityPrefix}-${identity}"
    Utils.runAzCommand(ctx, "az identity create -g $ctx.AZURE_POSTGRES_RESOURCE_GROUP_SND2 -n $identityName --tags 'ServiceCode=FFC' 'serviceName=FutureFarming' 'ServiceType=LOB' 'Environment=SND' 'Tier=ManagedIdentity' 'Location=northeurope' 'CreatedBy=JenkinsPipeline'")
  }

  private static def grantBusPrivileges(ctx, entityType, entityName, subscription="") {
    String identity = readManifestSingle(ctx, azureProvisionConfigFile, 'identity')
    def identityName = "${identityPrefix}-${identity}"
    def clientId = Utils.runAzCommand(ctx, "az identity show --resource-group $ctx.AZURE_POSTGRES_RESOURCE_GROUP_SND2 --name $identityName --query clientId --output tsv").trim()

    String busNamespace = "/subscriptions/$subscriptionSND2/resourceGroups/$ctx.AZURE_POSTGRES_RESOURCE_GROUP_SND2/providers/Microsoft.ServiceBus/namespaces/$ctx.AZURE_SERVICE_BUS_NAMESPACE_SND2"
    String entityId = entityType == "queue" ? "queues/$entityName" :
    entityType == "topic" ? "topics/$entityName" :
    entityType == "subscription" ? "topics/$entityName/$subscription" : ""
    if (entityId != "") {
      String serviceBusRole = "Azure Service Bus Data Owner" // Will read from provition yaml file (Azure Service Bus Data Sender/Azure Service Bus Data Receiver)
      //   --assignee-object-id $principalId \
      // --assignee-principal-type ServicePrincipal \
      Utils.runAzCommand(ctx, """
    az role assignment create \
    --role '${serviceBusRole}' \
    --assignee $clientId \
    --scope ${busNamespace}/${entityId}
    """)
    } else {
      ctx.echo("No EntityType!")
    }
  }

  private static def deleteGrantedBusPrivileges(ctx, entityType, entityName) {
    String identity = readManifestSingle(ctx, azureProvisionConfigFile, 'identity')
    def identityName = "${identityPrefix}-${identity}"
    def clientId = Utils.runAzCommand(ctx, "az identity show --resource-group $ctx.AZURE_POSTGRES_RESOURCE_GROUP_SND2 --name $identityName --query clientId --output tsv").trim()
    String busNamespace = "/subscriptions/$subscriptionSND2/resourceGroups/$ctx.AZURE_POSTGRES_RESOURCE_GROUP_SND2/providers/Microsoft.ServiceBus/namespaces/$ctx.AZURE_SERVICE_BUS_NAMESPACE_SND2"
    String entityId = entityType == "queue" ? "queues/$entityName" :
    entityType == "topic" ? "topics/$entityName" : ""
    if (entityId != "") {
      try {
        String serviceBusRole = "Azure Service Bus Data Owner" // Will read from provition yaml file (Azure Service Bus Data Sender/Azure Service Bus Data Receiver)
        ctx.sh("""
    az role assignment delete \
    --role '${serviceBusRole}' \
    --assignee $clientId \
    --scope ${busNamespace}/${entityId}
    """)
    } catch(e) {
        ctx.echo("Error: $e.message")
      }
    } else {
      ctx.echo("No EntityType!")
    }
  }

  // Database Operations

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
    // if local file not present, check the main branch - support both PR and cleanup
    return  hasResourcesToProvision(ctx, "./$migrationFile") || Utils.getUrlStatusCode(ctx, apiUrl) == "200"
  }

  private static def getMigrationFiles(ctx, destinationFolder) {
    def resourcePath = 'uk/gov/defra/ffc/migration'
    ctx.sh("rm -rf $destinationFolder")
    getResourceScript(ctx, "$resourcePath/scripts", 'schema-up', "$destinationFolder/scripts")
    getResourceScript(ctx, "$resourcePath/scripts", 'schema-down', "$destinationFolder/scripts")
    getResourceFile(ctx, resourcePath, 'docker-compose.migrate.yaml', destinationFolder)
    getResourceFile(ctx, "$resourcePath/changelog", 'schema.changelog.xml', "$destinationFolder/changelog")
  }

  private static def getResourceFile(ctx, resourcePath, filename, destinationFolder, makeExecutable = false) {
    def fileContent = ctx.libraryResource("$resourcePath/$filename")
    ctx.writeFile(file: "$destinationFolder/$filename", text: fileContent, encoding: "UTF-8")
    if (makeExecutable) {
      ctx.sh("chmod 777 ./$destinationFolder/$filename")
    }
    ctx.echo "written $filename to $destinationFolder"
  }

  private static def getResourceScript(ctx, resourcePath, filename, destinationFolder) {
    getResourceFile(ctx, resourcePath, filename, destinationFolder, true)
  }

  private static def getSchemaName(repoName, pr) {
    if (pr != '') {
      return repoName.replace('-', '_') + pr
    }
    return "public"
  }

  private static def getRepoPostgresEnvVars(ctx, environment, repoName, pr) {
    def schemaName = getSchemaName(repoName, pr)
    String database = createDatabaseName(ctx)

    return [
      "POSTGRES_DB=$database",
      "POSTGRES_SCHEMA_NAME=$schemaName"
    ]
  }

  private static def getUserPostgresEnvVars(ctx, environment, repoName, pr) {
    def postgresEnvVars = getPostgresAdminEnvVars(ctx)
    ctx.withEnv(postgresEnvVars) {
      return [
      "POSTGRES_SCHEMA_USERNAME=${ctx.POSTGRES_ADMIN_USERNAME}",
      "POSTGRES_SCHEMA_PASSWORD=${ctx.POSTGRES_ADMIN_PASSWORD}",
      "POSTGRES_SCHEMA_ROLE=${ctx.ADMIN_ROLE}"
    ]
    }
  }

  private static def createDatabaseName(ctx, database="") {
    String databaseName = database
    if (databaseName == "" && hasResourcesToProvision(ctx, azureProvisionConfigFile)) {
      databaseName = readManifestSingle(ctx, azureProvisionConfigFile, 'postgreSql.name')
    }
    return (databaseName != null && databaseName != '' ) ? "$databaseName-snd" : ''
  }

  private static def createDatabase(ctx, database="") {
    String databaseName = createDatabaseName(ctx, database)
    if (databaseName != "") {
      try {
        ctx.echo("Trying to create the database: $databaseName")
        runDatabaseCommand(ctx, "\'CREATE DATABASE \"$databaseName\"\'")
      } catch(e) {
        ctx.echo("Database Creation: $e.message")
      }
    }
  }

  private static def addDatabaseExtensions(ctx, database="") {
    String databaseName = createDatabaseName(ctx, database)
    if (databaseName != "" && hasResourcesToProvision(ctx, azureProvisionConfigFile)) {
      def databaseExtensions = readManifest(ctx, azureProvisionConfigFile, 'postgreSql.extensions')
      databaseExtensions.each {
        try {
          runDatabaseCommand(ctx, "\'CREATE EXTENSION IF NOT EXISTS \"$it\"\'",  databaseName)
        } catch(e) {
          ctx.echo("Extension Exists: $e.message")
        }
      }
    }
  }

  private static def grantDbPrivileges(ctx, repoName, pr, database="", user="") {
    String databaseName = createDatabaseName(ctx, database)
    if (databaseName != "") {
      String userName = user
      if (userName == "") {
        String identity = readManifestSingle(ctx, azureProvisionConfigFile, 'identity')
        userName = "${identityPrefix}-${identity}"
      }
      runDatabaseCommand(ctx, "\'GRANT ALL PRIVILEGES ON DATABASE \"$databaseName\" to \"$userName\"\'")
      runDatabaseCommand(ctx, "\'GRANT CONNECT ON DATABASE \"$databaseName\" to \"$userName\"\'")

      runDatabaseCommand(ctx, "\'GRANT USAGE on SCHEMA  \"public\" to \"$userName\"\'", databaseName)
      runDatabaseCommand(ctx, "\'GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA \"public\" to \"$userName\"\'", databaseName)
      runDatabaseCommand(ctx, "\'GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA \"public\" to \"$userName\"\'", databaseName)
      runDatabaseCommand(ctx, "\'GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA \"public\" to \"$userName\"\'", databaseName)
      if (pr != '') {
        def prSchema = getSchemaName(repoName, pr)
        runDatabaseCommand(ctx, "\'GRANT USAGE on SCHEMA  \"$prSchema\" to \"$userName\"\'", databaseName)
        runDatabaseCommand(ctx, "\'GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA \"$prSchema\" to \"$userName\"\'", databaseName)
        runDatabaseCommand(ctx, "\'GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA \"$prSchema\" to \"$userName\"\'", databaseName)
        runDatabaseCommand(ctx, "\'GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA \"$prSchema\" to \"$userName\"\'", databaseName)
      }
    }
  }

  private static def createDatabaseUser(ctx) {
    String databaseName = createDatabaseName(ctx)
    if (databaseName != "") {
      String identity = readManifestSingle(ctx, azureProvisionConfigFile, 'identity')
      def identityName = "${identityPrefix}-${identity}"
      try {
        runDatabaseCommand(ctx, "\"select * from pgaadauth_create_principal(\'$identityName\', false, false)\"")
      } catch(e) {
        ctx.echo("Role Exists: $e.message")
      }
    }
  }

  private static def getPostgresAdminEnvVars(ctx) {
    String adminUser = "AG-Azure-FFC-SND2FFCDBSSQ1001-AADAdmins"
    String password = Utils.runAzCommand(ctx, "az account get-access-token --resource-type oss-rdbms --query accessToken --output tsv")

    String postgresHost = "${ctx.AZURE_POSTGRES_HOST_SND2}.postgres.database.azure.com"
    return [
      "POSTGRES_ADMIN_USERNAME=${adminUser}",
      "POSTGRES_ADMIN_PASSWORD=${password}",
      "POSTGRES_HOST=${postgresHost}",
      "ADMIN_ROLE=${adminUser}"
    ]
  }

  private static def runDatabaseCommand(ctx, query, database="postgres") {
    def envVars = getPostgresAdminEnvVars(ctx)
    ctx.withEnv(envVars) {
      ctx.sh("""
    docker run --rm \
    -e PGPASSWORD="${ctx.POSTGRES_ADMIN_PASSWORD}" \
    -e PGHOST=${ctx.POSTGRES_HOST} \
    -e PGUSER=${ctx.POSTGRES_ADMIN_USERNAME} \
    -e PGDATABASE=$database \
    alpine/psql -c $query
    """)
    }
  }

}
