def createResources(repoName, pr) {
  def filePath = 'provision.azure.yaml'
  if(fileExists(filePath)) {
    deletePrResources(repoName, pr)   
    createAllResources(filePath, repoName, pr)
  }
}

def deleteBuildResources(repoName, pr) {
  deleteQueues("$repoName-build$BUILD_NUMBER-$pr-")
}

def createAllResources(filePath, repoName, pr) {
  def queues = readManifest(filePath, 'queues')
  createQueues(queues, repoName, pr)
}

def createQueues(queues, repoName, pr) {
  createBuildQueues(repoName, pr, queues)
  if(pr != '') {
    createPrQueues(repoName, pr, queues)
  }
}

def createPrQueues(repoName, pr) {
  queues.each {
    createQueue("$repoName-pr$pr-$it")
  }
}

def createBuildQueues(repoName, pr) {
  queues.each {
    createQueue("$repoName-build$BUILD_NUMBER-$pr-$it")
  }
}

def createQueue(queueName) {
  validateQueueName(queueName)  
  def azCommand = 'az servicebus queue create'
  sh("$azCommand ${getResGroupAndNamespace()} --name $queueName --max-size 1024")
}

def deletePrResources(repoName, pr) {
  deleteQueues("$repoName-pr$pr-")
}

def deleteQueues(prefix) {
  def queues = listQueues(prefix)
  queues.each {
    sh("az servicebus queue delete ${getResGroupAndNamespace()} --name $it")
  }
}

def validateQueueName(name) {
  assert name ==~ /^[A-Za-z0-9]$|^[A-Za-z0-9][\w-\.\/\~]*[A-Za-z0-9]$/ : "Invalid queue name: '$name'"
}

def readManifest(filePath, resource) {
 def resources = sh(returnStdout: true, script: "yq r $filePath resources.${resource}.*.name").trim()
 return resources.tokenize('\n')
}

def getResGroupAndNamespace () {
  return "--resource-group $AZURE_SERVICE_BUS_RESOURCE_GROUP --namespace-name $AZURE_SERVICE_BUS_NAMESPACE"
}

def listQueues(prefix) {
  def jqCommand = "jq -r '.[]| select(.name | startswith(\"$prefix\")) | .name'"
  def script = "az servicebus queue list ${getResGroupAndNamespace()} | $jqCommand"
  def queueNames = sh(returnStdout: true, script: script).trim()
  return queueNames.tokenize('\n')
}
