package uk.gov.defra.ffc

class Provision implements Serializable {
  static def createResources(ctx, repoName, pr) {
    def filePath = 'provision.azure.yaml'
    if(fileExists(filePath)) {
      deletePrResources(ctx, repoName, pr)   
      createAllResources(ctx, filePath, repoName, pr)
    }
  }

  static def deleteBuildResources(ctx, repoName, pr) {
    deleteQueues(ctx, getBuildQueuePrefix(repoName, pr))
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

  private static def createPrQueues(ctx, queues, repoName, pr) {
    queues.each {
      createQueue(ctx, "$repoName-pr$pr-$it")
    }
  }

  private static def createBuildQueues(ctx, queues, repoName, pr) {
    queues.each {
      createQueue(ctx, "${getBuildQueuePrefix(repoName, pr)}$it")
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
