
def createResources(repoName, pr) {
  def filePath = 'provision.azure.yaml'
  if(fileExists(filePath)) {
    //deletePrResources(repoName, pr)   
    createAllResources(filePath, repoName, pr)
  }
}

def fileExists(filePath){
  return sh(returnStdout: true, script: "test -f $filePath && echo true || echo false")
}

def readManifest(filePath, resource) {
 return sh(returnStdout: true, script: "yq r $filePath resources.$resource.**").trim()
}

// def deletePrResources(repoName, pr) {
//   deleteQueues(repoName, pr)
// }

// def deleteBuildQueues(repoName, pr) {
//   deleteQueues(repoName, pr)
// }

def createAllResources(filePath, repoName, pr) {
  def queues = readManifest(filePath, 'queues')
  createQueues(queues, repoName, pr)
}

// def deleteQueues(repoName, pr) {
//   def resourceGroup = AZURE_SERVICE_BUS_RESOURCE_GROUP
//   def namespace = AZURE_SERVICE_BUS_NAMESPACE
//   def queues = sh("az servicebus queue list --resource-group $resourceGroup --namespace-name $namespace | jq. // TODO get PR queues that match convention")
//   // az servicebus queue list --resource-group SNDFFCINFRG1001 --namespace-name SNDFFCINFSB1001  | jq -r '.[]| select(.name | startswith("mh")) | .name'
//   foreach(queues in queues) {
//     sh('az servicebus queue delete')
//   }
// }

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
