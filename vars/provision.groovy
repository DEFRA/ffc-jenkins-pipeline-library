
def createResources(repoName, pr) {
  def filePath = 'provision.azure.yaml'
  if(fileExists(filePath)) {
    deletePrResources(repoName, pr)   
    createAllResources(filePath, repoName, pr)
  }
}

def deletePrResources(repoName, pr) {
  deleteQueues("$repoName-pr$pr-")
}

def deleteQueues(prefix) {
  def queues = listQueues(prefix)
  queues.each {
    sh("az servicebus queue delete --resource-group $AZURE_SERVICE_BUS_RESOURCE_GROUP --namespace-name $AZURE_SERVICE_BUS_NAMESPACE --name $it")
  }
}

def validateQueueName(name) {
  assert name ==~ /^[A-Za-z0-9]$|^[A-Za-z0-9][\w-\.\/\~]*[A-Za-z0-9]$/ : "Invalid queue name: '$name'"
}

def listQueues(prefix) {
  def resGroupAndNamespace = "--resource-group $AZURE_SERVICE_BUS_RESOURCE_GROUP --namespace-name $AZURE_SERVICE_BUS_NAMESPACE"
  def jqCommand = "jq -r '.[]| select(.name | startswith(\"$prefix\")) | .name'"
  def script = "az servicebus queue list $resGroupAndNamespace | $jqCommand"
  def queueNames = sh(returnStdout: true, script: script).trim()
  return queueNames.tokenize('\n')
}

def readManifest(filePath, resource) {
 def resources = sh(returnStdout: true, script: "yq r $filePath resources.$resource.*.name").trim()
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
    createQueue("$repoName-build$BUILD_NUMBER-$pr-$key")
  }
}

def createQueue(queueName) {
  validateQueueName(queueName)
  sh("az servicebus queue create --resource-group $AZURE_SERVICE_BUS_RESOURCE_GROUP --namespace-name $AZURE_SERVICE_BUS_NAMESPACE --name $queueName --max-size 1024")
}
