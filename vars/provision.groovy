provisionFilePath = 'provision.azure.yaml'

def createResources(repoName, pr) {
  if(fileExists(provisionFilePath)) {
    //deletePrResources(repoName, pr)   
    createAllResources(repoName, pr)
  }
}

def fileExists(filePath){
  return sh("test -f $filePath && echo true || echo false")
}

def readManifest(resource) {
 return sh("yq r $provisionFilePath resources.$resource.**")
}

// def deletePrResources(repoName, pr) {
//   deleteQueues(repoName, pr)
// }

// def deleteBuildQueues(repoName, pr) {
//   deleteQueues(repoName, pr)
// }

def createAllResources(repoName, pr) {
  createQueues(readManifest('queues'), repoName, pr)
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
  sh("az servicebus queue create --resource-group $AZURE_SERVICE_BUS_RESOURCE_GROUP --namespace-name $AZURE_SERVICE_BUS_NAMESPACE --name $queue")
}
