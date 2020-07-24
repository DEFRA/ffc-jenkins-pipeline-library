
def createResources(repoName, pr) {
  def filePath = 'provision.azure.yaml'
  if(fileExists(filePath)) {
    deletePrResources(repoName, pr)   
    createAllResources(filePath, repoName, pr)
  }
}

def readManifest(filePath, resource) {
 def resources = sh(returnStdout: true, script: "yq r $filePath resources.$resource.**").trim()
 return resources.tokenize('\n')
}

def createAllResources(filePath, repoName, pr) {
  def queues = readManifest(filePath, 'queues')
  createQueues(queues, repoName, pr)
}

def createQueues(queues, repoName, pr) {
  queues.each { key ->
    if(pr != '') {
      createQueue("$repoName-pr$pr-$key")
    }
    createQueue("$repoName-$BUILD_NUMBER-$pr-$key")
  }
}

def createQueue(queueName) {
  sh(returnStdout: true, script:"az servicebus queue create --resource-group $AZURE_SERVICE_BUS_RESOURCE_GROUP --namespace-name $AZURE_SERVICE_BUS_NAMESPACE --name $queueName")
}
