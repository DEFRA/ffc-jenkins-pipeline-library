def createResources(repoName, pr) {
  if(manifest.azure) {
    deletePrResources(repoName, pr)   
    createAllResources(pr)
  }
}

def readManifest() {
  return manifest
}

def validateQueueName(name) {
  assert name ==~ /^[A-Za-z0-9]$|^[A-Za-z0-9][\w-\.\/\~]*[A-Za-z0-9]$/ : "Invalid queue name: '$name'"
}

def listQueues(prefix) {
  validateQueueName(prefix)
  def resGroupAndNamespace = "--resource-group $AZURE_SERVICE_BUS_RESOURCE_GROUP --namespace-name $AZURE_SERVICE_BUS_NAMESPACE"
  def jqCommand = "jq -r '.[]| select(.name | startswith(\"$prefix\")) | .name'"
  def script = "az servicebus queue list $resGroupAndNamespace | $jqCommand"
  def queueNames = sh(returnStdout: true, script: script).trim()
  return queueNames.tokenize('\n')
}

def deletePrResources(repoName, pr) {
  deleteQueues(repoName, pr)
}

def deleteBuildQueues(repoName, pr) {
  deleteQueues(repoName, pr)
}

def createAllResources(pr) {
  def manifest = readManifest()
  if(manifest.queues) {
    createQueues(manifest.queues, pr)
  }
}

def deleteQueues(repoName, pr) {
  def resourceGroup = AZURE_SERVICE_BUS_RESOURCE_GROUP
  def resourceGroup = AZURE_SERVICE_BUS_NAMESPACE
  def queues = listQueues(resourceGroup, resourceGroup, 'sp')
  foreach(queue in queues) {
    sh("az servicebus queue delete --resource-group $resourceGroup --namespace-name $namespace --name $queue")
  }
}

/* def createQueues(queues, pr) {
  foreach(queue in queues) {
    if(pr) {
      createQueue(pr queue name)
    }
    createQueue(build queue name)
  }
}
 */
def createQueue(queue) {
  sh("az servicebus queue create --name $queue")
}
