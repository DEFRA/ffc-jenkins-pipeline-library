package uk.gov.defra.ffc
def branch = ''
def pr = ''
def mergedPrNo = ''
def containerTag = ''
def repoUrl = ''
def commitSha = ''
def workspace

def provisionInfrastructure(target, item, parameters) {
  sshagent(['helm-chart-creds']) {
    echo "provisionInfrastructure"
    if (target.toLowerCase() == "aws") {
      switch (item) {
        case "sqs":
          dir('terragrunt') {
            sh "pwd"
            echo "cloning terraform repo"
            // git clone repo...
            git credentialsId: 'helm-chart-creds', url: 'git@gitlab.ffc.aws-int.defra.cloud:terraform_sqs_pipelines/terragrunt_sqs_queues.git'
            // sh "git clone git@gitlab.ffc.aws-int.defra.cloud:terraform_sqs_pipelines/terragrunt_sqs_queues.git"
            echo "copy queue dir into new dir"
            // cd into repo...
            sh "cd london/eu-west-2/ffc/ ; cp -fr standard_sqs_queues pr${parameters["pr_code"]}"
            // copy queue dir into new dir...
            // sh "cp -fr standard_sqs_queues pr${parameters["pr_code"]}"
            // sh "cd pr${parameters["pr_code"]}" 
            // run terragrunt...
            echo "provision infrastructure"
            //sh "cd london/eu-west-2/ffc/pr${parameters["pr_code"]} ; pwd"
            sh "cd london/eu-west-2/ffc/pr${parameters["pr_code"]} ; terragrunt apply -var \"pr_code=${parameters["pr_code"]}\" -auto-approve"          
            echo "TERROR!!! apply -var \"pr_code=${parameters["pr_code"]}\" -auto-approve"
            echo "infrastructure successfully provisioned"
          }
          break;
        default:
          error("provisionInfrastructure error: unsupported item ${item}")
      }
    } else {
      error("provisionInfrastructure error: unsupported target ${target}")
    } 
  }
}

def getCSProjVersion(projName) {
  return sh(returnStdout: true, script: "xmllint ${projName}/${projName}.csproj --xpath '//Project/PropertyGroup/Version/text()'").trim()
}

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

def getVariables(repoName, version) {
    branch = BRANCH_NAME
    // use the git API to get the open PR for a branch
    // Note: This will cause issues if one branch has two open PRs
    pr = sh(returnStdout: true, script: "curl https://api.github.com/repos/DEFRA/$repoName/pulls?state=open | jq '.[] | select(.head.ref == \"$branch\") | .number'").trim()
    verifyCommitBuildable()
      
    def rawTag
    if (branch == "master") {
      rawTag = version
    } else {
      rawTag = pr == '' ? branch : "pr$pr"
    }

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
