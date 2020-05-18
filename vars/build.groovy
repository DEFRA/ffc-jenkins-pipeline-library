import uk.gov.defra.ffc.Build

// public
def getVariables(version) {
  return Build.getVariables(this)
}

// public
def setGithubStatusSuccess(message = 'Build successful') {
  Build.updateGithubCommitStatus(this, message, 'SUCCESS')
}

// public
def setGithubStatusPending(message = 'Build started') {
  Build.updateGithubCommitStatus(this, message, 'PENDING')
}

// public
def setGithubStatusFailure(message = '') {
  Build.updateGithubCommitStatus(this, message, 'FAILURE')
}

// public
def buildTestImage(credentialsId, registry, projectName, buildNumber) {
  docker.withRegistry("https://$registry", credentialsId) {
    sh "docker-compose -p $projectName-$identityTag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml build --no-cache"
  }
}

// public
def runTests(projectName, serviceName, buildNumber) {
  try {
    sh 'mkdir -p test-output'
    sh 'chmod 777 test-output'
    sh "docker-compose -p $projectName-$identityTag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml run $serviceName"
  } finally {
    sh "docker-compose -p $projectName-$identityTag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml down -v"
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
