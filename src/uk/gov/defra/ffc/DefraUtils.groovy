package uk.gov.defra.ffc
def branch = ''
def pr = ''
def mergedPrNo = ''
def containerTag = ''
def repoUrl = ''
def commitSha = ''
def workspace

def getPackageJsonVersion() {
   return sh(returnStdout: true, script: "jq -r '.version' package.json").trim()
}

def replaceInFile(from, to, file) {
  sh "sed -i -e 's/$from/$to/g' $file"  
}

def getMergedPrNo() {
    def mergedPrNo = sh(returnStdout: true, script: "git log --pretty=oneline --abbrev-commit -1 | sed -n 's/.*(#\\([0-9]\\+\\)).*/\\1/p'").trim()
    return mergedPrNo ? "pr$mergedPrNo" : ''
}

def getRepoUrl() {
  return sh(returnStdout: true, script: "git config --get remote.origin.url").trim()
}

def getCommitSha() {
  return sh(returnStdout: true, script: "git rev-parse HEAD").trim()
}

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

def getVariables(repoName) {
    branch = BRANCH_NAME
    // use the git API to get the open PR for a branch
    // Note: This will cause issues if one branch has two open PRs
    pr = sh(returnStdout: true, script: "curl https://api.github.com/repos/DEFRA/$repoName/pulls?state=open | jq '.[] | select(.head.ref == \"$branch\") | .number'").trim()
    verifyCommitBuildable()
    def rawTag = pr == '' ? branch : "pr$pr"
    containerTag = rawTag.replaceAll(/[^a-zA-Z0-9]/, '-').toLowerCase()
    mergedPrNo = getMergedPrNo()
    repoUrl = getRepoUrl()
    commitSha = getCommitSha()
    return [pr, containerTag, mergedPrNo]
}

def updateGithubCommitStatus(message, state) {
  step([
    $class: 'GitHubCommitStatusSetter',
    reposSource: [$class: "ManuallyEnteredRepositorySource", url: repoUrl],
    commitShaSource: [$class: "ManuallyEnteredShaSource", sha: commitSha],
    errorHandlers: [[$class: 'ShallowAnyErrorHandler']],
    statusResultSource: [ $class: "ConditionalStatusResultSource", results: [[$class: "AnyBuildResult", message: message, state: state]] ]
  ])
}

def setGithubStatusSuccess(message = 'Build successful') {
  updateGithubCommitStatus(message, 'SUCCESS')
}

def setGithubStatusPending(message = 'Build started') {
  updateGithubCommitStatus(message, 'PENDING')
}

def setGithubStatusFailure(message = '') {
  updateGithubCommitStatus(message, 'FAILURE')
}

def lintHelm(imageName) {
  sh "helm lint ./helm/$imageName"
}

def buildTestImage(name, suffix) {
  sh 'docker image prune -f || echo could not prune images'
  sh "docker-compose -p $name-$suffix-$containerTag -f docker-compose.yaml -f docker-compose.test.yaml build --no-cache $name"
}

def runTests(name, suffix) {
  try {
    sh 'mkdir -p test-output'
    sh 'chmod 777 test-output'
    sh "docker-compose -p $name-$suffix-$containerTag -f docker-compose.yaml -f docker-compose.test.yaml run $name"

  } finally {
    sh "docker-compose -p $name-$suffix-$containerTag -f docker-compose.yaml -f docker-compose.test.yaml down -v"    
  }
}

def createTestReportJUnit(){
  junit 'test-output/junit.xml'
}

def deleteTestOutput(name) {
    // clean up files created by node/ubuntu user that cannot be deleted by jenkins. Note: uses global environment variable
    sh "docker run --rm -u node --mount type=bind,source='$WORKSPACE/test-output',target=/usr/src/app/test-output $name rm -rf test-output/*"  
}

def analyseCode(sonarQubeEnv, sonarScanner, params) {
  def scannerHome = tool sonarScanner
  withSonarQubeEnv(sonarQubeEnv) { 
    def args = ''   
    params.each { param ->
      args = args + " -D$param.key=$param.value"
    }

    sh "${scannerHome}/bin/sonar-scanner$args"
  }
}

def waitForQualityGateResult(timeoutInMinutes) {
  timeout(time: timeoutInMinutes, unit: 'MINUTES') {
    def qualityGateResult = waitForQualityGate()
    if (qualityGateResult.status != 'OK') {
      error "Pipeline aborted due to quality gate failure: ${qualityGateResult.status}"
    }
  }
}

def buildAndPushContainerImage(credentialsId, registry, imageName, tag) {
  docker.withRegistry("https://$registry", credentialsId) {
    sh "docker-compose build --no-cache"
    sh "docker tag $imageName $registry/$imageName:$tag"
    sh "docker push $registry/$imageName:$tag"
  }
}

def deployChart(credentialsId, registry, imageName, tag, extraCommands) {
  withKubeConfig([credentialsId: credentialsId]) {
    def deploymentName = "$imageName-$tag"
    sh "kubectl get namespaces $deploymentName || kubectl create namespace $deploymentName"
    sh "helm upgrade $deploymentName --install --namespace $deploymentName --atomic ./helm/$imageName --set image=$registry/$imageName:$tag $extraCommands"
  }
}

def undeployChart(credentialsId, imageName, tag) {
  def deploymentName = "$imageName-$tag"
  echo "removing deployment $deploymentName"
  withKubeConfig([credentialsId: credentialsId]) {
    sh "helm delete --purge $deploymentName || echo error removing deployment $deploymentName"
    sh "kubectl delete namespaces $deploymentName || echo error removing namespace $deploymentName"
  }
}

def publishChart(registry, imageName, containerTag) {
  withCredentials([
    string(credentialsId: 'helm-chart-repo', variable: 'helmRepo')
  ]) {
    // jenkins doesn't tidy up folder, remove old charts before running
    sh "rm -rf helm-charts"
    sshagent(credentials: ['helm-chart-creds']) {
      sh "git clone $helmRepo"
      dir('helm-charts') {
        sh 'helm init -c'
        sh "sed -i -e 's/image: $imageName/image: $registry\\/$imageName:$containerTag/' ../helm/$imageName/values.yaml"
        sh "helm package ../helm/$imageName"
        sh 'helm repo index .'
        sh 'git config --global user.email "buildserver@defra.gov.uk"'
        sh 'git config --global user.name "buildserver"'
        sh 'git checkout master'
        sh 'git add -A'
        sh "git commit -m 'update $imageName helm chart from build job'"
        sh 'git push'
      }
    }
  }
}

def triggerDeploy(jenkinsUrl, jobName, token, params) {
  def url = "$jenkinsUrl/job/$jobName/buildWithParameters?token=$token"
  params.each { param ->
    url = url + "\\&amp;$param.key=$param.value"
  }
  echo "Triggering deployment for $url"
  sh(script: "curl -k $url")
}

def releaseExists(containerTag, repoName, token){
    
    //temp
    containerTag = "1.0.1"

     //latestReleaseNum = sh(returnStatus: true, script: "curl --silent -H 'Authorization: token $token' https://api.github.com/repos/DEFRA/$repoName/releases/latest |jq '.tag_name'")  
    doesReleaseExist = sh(returnStatus: true, script: "curl --silent -H 'Authorization: token $token' https://api.github.com/repos/DEFRA/$repoName/releases |jq '.[].tag_name | index(''1.0.1'') | select (. != null) | tostring | test(''0'')'")
    echo "containerTag valiue is $containerTag"
    echo "doesReleaseExist value is $doesReleaseExist"
    if (doesReleaseExist){
      return true
    } else {
      return false
    }
}

def triggerRelease(containerTag, repoName, releaseDescription, token){
    if (releaseExists(containerTag, repoName, token)){
      echo "The release already exists so not creating new one!"
      return
    }

    //need to create new function to check if there is an existing release with same tag if so dont create new release just skip.

    echo "Triggering release for $repoName"
    def outfile = 'stdout.out'
    result = sh(returnStatus: true, script: "curl -X POST -H 'Authorization: token $token'  -d '{'tag_name': $containerTag, 'name':'Release $containerTag,'body':$releaseDescription}'  https://api.github.com/repos/DEFRA/$repoName/releases >${outfile} 2>&1")
    def output = readFile(outfile).trim()
    if (result != 0){
      echo "Failed to trigger release for $repoName"
      throw new Exception (output)
    } else {
      echo "Release for $repoName successfully completed"
    }
}

def notifySlackBuildFailure(exception, channel) {

  def msg = """BUILD FAILED 
          ${JOB_NAME}/${BUILD_NUMBER}
          ${exception}
          (<${BUILD_URL}|Open>)"""

  if(JOB_NAME.contains("/master/")) {
    msg = '@here '.concat(msg);
    channel = "#masterbuildfailures"
  }

  slackSend channel: channel,
            color: "#ff0000",
            message: msg.replace("  ", "")

}

return this
