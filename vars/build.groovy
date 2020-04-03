def branch = ''
def pr = ''
def mergedPrNo = ''
def containerTag = ''
def repoUrl = ''
def commitSha = ''
def workspace

// private
def getMergedPrNo() {
  def mergedPrNo = sh(returnStdout: true, script: "git log --pretty=oneline --abbrev-commit -1 | sed -n 's/.*(#\\([0-9]\\+\\)).*/\\1/p'").trim()
  return mergedPrNo ? "pr$mergedPrNo" : ''
}

// private
def getRepoUrl() {
  return sh(returnStdout: true, script: "git config --get remote.origin.url").trim()
}

// private
def getRepoName() {
  return scm.getUserRemoteConfigs()[0].getUrl().tokenize('/').last().split("\\.git")[0]
}

// private
def getCommitSha() {
  return sh(returnStdout: true, script: "git rev-parse HEAD").trim()
}

// private
def verifyCommitBuildable() {
  if (pr) {
    echo "Building PR$pr"
  } else if (branch == "master") {
    echo "Building master branch"
  } else {
    currentBuild.result = 'ABORTED'
    error('Build aborted - not a PR or a master branch')
  }
}

// public
def getVariables(repoName, version) {
    branch = BRANCH_NAME
    // use the git API to get the open PR for a branch
    // Note: This will cause issues if one branch has two open PRs
    pr = sh(returnStdout: true, script: "curl https://api.github.com/repos/DEFRA/$repoName/pulls?state=open | jq '.[] | select(.head.ref == \"$branch\") | .number'").trim()
    verifyCommitBuildable()

    if (branch == "master") {
      containerTag = version
    } else {
      def rawTag = pr == '' ? branch : "pr$pr"
      containerTag = rawTag.replaceAll(/[^a-zA-Z0-9]/, '-').toLowerCase()
    }

    mergedPrNo = getMergedPrNo()
    repoUrl = getRepoUrl()
    def repoName = getRepoName()
    commitSha = getCommitSha()
    return [repoName, pr, containerTag, mergedPrNo]
}

// private
def updateGithubCommitStatus(message, state) {
  step([
    $class: 'GitHubCommitStatusSetter',
    reposSource: [$class: "ManuallyEnteredRepositorySource", url: repoUrl],
    commitShaSource: [$class: "ManuallyEnteredShaSource", sha: commitSha],
    errorHandlers: [[$class: 'ShallowAnyErrorHandler']],
    statusResultSource: [ $class: "ConditionalStatusResultSource", results: [[$class: "AnyBuildResult", message: message, state: state]] ]
  ])
}

// public
def setGithubStatusSuccess(message = 'Build successful') {
  updateGithubCommitStatus(message, 'SUCCESS')
}

// public
def setGithubStatusPending(message = 'Build started') {
  updateGithubCommitStatus(message, 'PENDING')
}

// public
def setGithubStatusFailure(message = '') {
  updateGithubCommitStatus(message, 'FAILURE')
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
