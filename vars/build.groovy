import uk.gov.defra.ffc.Utilities

def getVariables() {
  def utilities = new Utilities(this)
  return utilities.getVariables()
}

def setGithubStatusSuccess(message = 'Build successful') {
  def utilities = new Utilities(this)
  utilities.updateGithubCommitStatus(message, 'SUCCESS')
}

def setGithubStatusPending(message = 'Build started') {
  def utilities = new Utilities(this)
  utilities.updateGithubCommitStatus(message, 'PENDING')
}

def setGithubStatusFailure(message = '') {
  def utilities = new Utilities(this)
  utilities.updateGithubCommitStatus(message, 'FAILURE')
}

// public
def buildTestImage(credentialsId, registry, projectName, buildNumber) {
  docker.withRegistry("https://$registry", credentialsId) {
    sh "docker-compose -p $projectName-$containerTag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml build --no-cache"
  }
}

// public
def runTests(projectName, serviceName, buildNumber) {
  try {
    sh 'mkdir -p test-output'
    sh 'chmod 777 test-output'
    sh "docker-compose -p $projectName-$containerTag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml run $serviceName"
  } finally {
    sh "docker-compose -p $projectName-$containerTag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml down -v"
  }
}

// public
def buildAndPushContainerImage(credentialsId, registry, imageName, tag) {
  docker.withRegistry("https://$registry", credentialsId) {
    sh "docker-compose -f docker-compose.yaml build --no-cache"
    sh "docker tag $imageName $registry/$imageName:$tag"
    sh "docker push $registry/$imageName:$tag"
  }
}
