def createResources(repoName, pr) {
  if(manifest.azure) {
    deletePrResources(repoName, pr)   
    createAllResources(pr)
  }
}

def readManifest() {
  return manifest
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
  def namespace = AZURE_SERVICE_BUS_NAMESPACE
  def queues = sh("az servicebus queue list --resource-group $resourceGroup --namespace-name $namespace | jq. // TODO get PR queues that match convention")
  // az servicebus queue list --resource-group SNDFFCINFRG1001 --namespace-name SNDFFCINFSB1001  | jq -r '.[]| select(.name | startswith("mh")) | .name'
  foreach(queues in queues) {
    sh('az servicebus queue delete')
  }
}

def createQueues(queues, pr) {
  foreach(queue in queues) {
    if(pr) {
      createQueue(pr queue name)
    }
    createQueue(build queue name)
  }
}

def createQueue(queue) {
  sh("az servicebus queue create --name $queue")
}
