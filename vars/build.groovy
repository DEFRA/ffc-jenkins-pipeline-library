import uk.gov.defra.ffc.Build

def getVariables(version) {
  return Build.getVariables(this, version)
}

def setGithubStatusSuccess(message = 'Build successful') {
  Build.updateGithubCommitStatus(this, message, 'SUCCESS')
}

def setGithubStatusPending(message = 'Build started') {
  Build.updateGithubCommitStatus(this, message, 'PENDING')
}

def setGithubStatusFailure(message = '') {
  Build.updateGithubCommitStatus(this, message, 'FAILURE')
}

def buildTestImage(credentialsId, registry, projectName, buildNumber) {
  docker.withRegistry("https://$registry", credentialsId) {
    sh("docker-compose -p $projectName-$identityTag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml build --no-cache")
  }
}

def runTests(projectName, serviceName, buildNumber) {
  try {
    sh('mkdir -p test-output')
    sh('chmod 777 test-output')
    sh("docker-compose -p $projectName-$identityTag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml run $serviceName")
  } finally {
    sh("docker-compose -p $projectName-$identityTag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml down -v")
  }
}

def buildAndPushContainerImage(credentialsId, registry, imageName, tag) {
  docker.withRegistry("https://$registry", credentialsId) {
    sh('docker-compose -f docker-compose.yaml build --no-cache')
    sh("docker tag $imageName $registry/$imageName:$tag")
    sh("docker push $registry/$imageName:$tag")
  }
}
