package uk.gov.defra.ffc

import uk.gov.defra.ffc.Helm

class Provision implements Serializable {
  static def createResources(ctx, repoName, pr) {
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
/*
  private static def createPrDatabase(ctx, repoName, pr) {
    if (pr != '' && ctx.fileExists('./docker-compose.migrate.yaml')) {
      def schemaName = repoName.replace('-','_') + pr
      def schemaRole = repoName.replace('-','_') + pr + "role"
      def envVars = "POSTGRES_HOST=${AZURE_DB_HOST} " +
                     "POSTGRES_USER=${AZURE_DB_USER} " +
                     "POSTGRES_PASSWORD=${AZURE_DB_PASSWORD} " +
                     "SCHEMA_ROLE=$schemaRole " +
                     "SCHEMA_PASSWORD=${AZURE_PR_PASSWORD} " +
                     "SCHEMA_NAME=$schemaName"

       ctx.sh "$envVars docker-compose -p $repoName-$pr -f docker-compose.migrate.yaml run schema-up"
       ctx.sh "$envVars docker-compose -p $repoName-$pr -f docker-compose.migrate.yaml run database-up"
    }
  }*/

  private static def getDatabaseEnvVars(ctx) {
    def searchKeys = [
      'postgresService.postgresExternalName',
      'postgresAdminUser',
      'postgresAdminPassword',
      'postgresSchemaPassword'
    ]
    // def appConfigPrefix = environment + '/'
    def appConfigPrefix ='dev/'
    def values = Helm.getConfigValues(ctx, searchKeys, appConfigPrefix)
    ef envs = values.collect { "$it.key=$it.value" }.join(' ')
    envs = envs.replace('postgresHost', 'POSTGRES_HOST')
    envs = envs.replace('postgresAdminUser', 'POSTGRES_USER')
    envs = envs.replace('postgresAdminPassword', 'POSTGRES_PASSWORD')
    envs = envs.replace('postgresSchemaPassword', 'SCHEMA_PASSWORD')
    return envs
  }
/*
  private static def deletePrDatabase(ctx, repoName, pr) {
    if (pr != '' && ctx.fileExists('./docker-compose.migrate.yaml')) {
      def schemaName = repoName.replace('-','_') + pr
      def schemaRole = repoName.replace('-','_') + pr + "role"
      def envVars = "POSTGRES_HOST=${AZURE_DB_HOST} " +
                     "POSTGRES_USER=${AZURE_DB_USER} " +
                     "POSTGRES_PASSWORD=${AZURE_DB_PASSWORD} " +
                     "SCHEMA_ROLE=$schemaRole " +
                     "SCHEMA_PASSWORD=${AZURE_PR_PASSWORD} " +
                     "SCHEMA_NAME=$schemaName"

       ctx.sh "$envVars docker-compose -p $repoName-$pr -f docker-compose.migrate.yaml run database-down"
       ctx.sh "$envVars docker-compose -p $repoName-$pr -f docker-compose.migrate.yaml run schema-down"
    }
  }
*/
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
