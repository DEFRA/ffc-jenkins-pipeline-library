package uk.gov.defra.ffc
def branch = ''
def pr = ''
def mergedPrNo = ''
def containerTag = ''
def repoUrl = ''
def commitSha = ''
def workspace

def Boolean __hasKeys(Map mapToCheck, List keys) {
    def passes = true;
    for (key in keys) {
        if (key instanceof Map) {
            assert key.size() == 1 : "keys to check should not have a Map with more than one key";
            def keyToCheck = key.keySet()[0];
            if (mapToCheck.containsKey(keyToCheck)) {
                if (!__hasKeys(mapToCheck[keyToCheck], key.values()[0])) {
                    passes = false;
                }
            } else {
                passes = false;
            }
        } else {
            if (!mapToCheck.containsKey(key)) {
                passes = false;
            }
        }
    }
    return passes;
}

def String[] __mapToString(Map map) {
    def output = [];
    for (item in map) {
        if (item.value instanceof String) {
            output.add("${item.key} = \"${item.value}\"");
        } else if (item.value instanceof Map) {
            output.add("${item.key} = {\n\t${__mapToString(item.value).join("\n\t")}\n}");
        } else {
            output.add("${item.key} = ${item.value}");
        }
    }
    return output;
}

def String __generateTerraformInputVariables(Map inputs) {
    return __mapToString(inputs).join("\n")
}

def destroyInfrastructure(target, item, parameters) {
  sshagent(['helm-chart-creds']) {
    echo "destroyInfrastructure"
    if (target.toLowerCase() == "aws") {
      switch (item) {
        case "sqs":
          dir('terragrunt') {
            // git clone repo...
            git credentialsId: 'helm-chart-creds', url: 'git@gitlab.ffc.aws-int.defra.cloud:terraform_sqs_pipelines/terragrunt_sqs_queues.git'
            // terragrunt destroy
            echo "HORROR!!! destroy -var \"pr_code=${parameters["pr_code"]}\" -auto-approve"
            sh "cd london/eu-west-2/ffc/pr${parameters["pr_code"]} ; terragrunt destroy -var \"pr_code=${parameters["pr_code"]}\" -auto-approve"
            // delete the pr dir
            echo "cd london/eu-west-2/ffc/ ; git rm -fr pr${parameters["pr_code"]}"
            sh "cd london/eu-west-2/ffc/ ; git rm -fr pr${parameters["pr_code"]}"
            // commit the changes back
            echo "git commit -am \"Delete PR${parameters["pr_code"]} SQS queue config\" ; git push --set-upstream origin master"
            sh "git commit -am \"Delete PR${parameters["pr_code"]} SQS queue config\" ; git push --set-upstream origin master"
            echo "infrastructure successfully destroyed"
            // Recursively delete the current dir (which should be terragrunt in the current job workspace)
            deleteDir()
          }
          break;
        default:
          error("destroyInfrastructure error: unsupported item ${item}")
      }
    } else {
      error("destroyInfrastructure error: unsupported target ${target}")
    }
  }
}

def provisionInfrastructure(target, item, parameters) {
  sshagent(['helm-chart-creds']) {
    echo "provisionInfrastructure"
    if (target.toLowerCase() == "aws") {
      switch (item) {
        case "sqs":
          assert __hasKeys(parameters, [['service': ['code', 'name', 'type']], 'pr_code', 'queue_purpose', 'repo_name']) :
            "parameters should specify pr_code, queue_purpose, repo_name as well as service details (code, name and type)";
          assert parameters['repo_name'].size() + parameters['pr_code'].toString().size() + parameters['queue_purpose'].size() < 76 :
            "repo name, pr code and queue purpose parameters should have fewer than 76 characters when combined";
          dir('terragrunt') {
            sh "pwd"
            echo "cloning terraform repo"
            // git clone repo...
            git credentialsId: 'helm-chart-creds', url: 'git@gitlab.ffc.aws-int.defra.cloud:terraform_sqs_pipelines/terragrunt_sqs_queues.git'

            dir('london/eu-west-2/ffc') {
              def dirName = "${parameters["repo_name"]}-pr${parameters["pr_code"]}"
              if (!fileExists("${dirName}/terraform.tfvars")) {
                echo "${dirName} directory doesn't exist, creating..."
                echo "copy queue dir into new dir"
                // cd into repo, copy queue dir into new dir...
                sh "cp -fr standard_sqs_queues ${dirName}"
                echo "adding new dir to git repo"
                sh "git add ${dirName} ; git commit -m \"${dirName}\" ; git push --set-upstream origin master"
              }
              def varFileName = "vars-${(new Date()).getTime().toString()}.tfvars"
              dir(dirName) {
                writeFile(varFileName, __generateTerraformInputVariables(parameters))
                // sh "git add ${varFileName} ; git commit -m \"${varFileName}\" ; git push --set-upstream origin master"
                // echo "provision infrastructure"
                // sh "terragrunt apply -var-file='${varFileName}' -auto-approve"
              }
              echo "infrastructure successfully provisioned"
            }
            // Recursively delete the current dir (which should be terragrunt in the current job workspace)
            // deleteDir()
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

def getCSProjVersionMaster(projName) {
  return sh(returnStdout: true, script: "git show origin/master:${projName}/${projName}.csproj | xmllint --xpath '//Project/PropertyGroup/Version/text()' -").trim()
}

def getPackageJsonVersion() {
  return sh(returnStdout: true, script: "jq -r '.version' package.json").trim()
}

def getPackageJsonVersionMaster() {
  return sh(returnStdout: true, script: "git show origin/master:package.json | jq -r '.version'").trim()
}

def verifyCSProjVersionIncremented(projectName) {
  def masterVersion = getCSProjVersionMaster(projectName)
  def version = getCSProjVersion(projectName)
  errorOnNoVersionIncrement(masterVersion, version)
}

def verifyPackageJsonVersionIncremented() {
  def masterVersion = getPackageJsonVersionMaster()
  def version = getPackageJsonVersion()
  errorOnNoVersionIncrement(masterVersion, version)
}

def errorOnNoVersionIncrement(masterVersion, version){
  if (versionHasIncremented(masterVersion, version)) {
    echo "version increment valid '$masterVersion' -> '$version'"
  } else {
    error( "version increment invalid '$masterVersion' -> '$version'")
  }
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

def getCommitMessage() {
  return sh(returnStdout: true, script: 'git log -1 --pretty=%B | cat')
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
    
    if (branch == "master") {
      containerTag = version
    } else {
      def rawTag = pr == '' ? branch : "pr$pr"
      containerTag = rawTag.replaceAll(/[^a-zA-Z0-9]/, '-').toLowerCase()
    }

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
    sh "[ -d \"$WORKSPACE/test-output\" ] && docker run --rm -u node --mount type=bind,source='$WORKSPACE/test-output',target=/usr/src/app/test-output $name rm -rf test-output/*"  
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
        sh "sed -i -e 's/version:.*/version: $containerTag/' ../helm/$imageName/Chart.yaml"
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
    try {
      def result = sh(returnStdout: true, script: "curl -s -H 'Authorization: token $token' https://api.github.com/repos/DEFRA/$repoName/releases/tags/$containerTag | jq '.tag_name'").trim().replaceAll (/"/, '') == "$containerTag" ? true : false
      return result
    }
      catch(Exception ex) {
      echo "Failed to check release status on github"
      throw new Exception (ex)
    }  
}

def triggerRelease(containerTag, repoName, releaseDescription, token){
    if (releaseExists(containerTag, repoName, token)){
      echo "Release $containerTag already exists"
      return
    }

    echo "Triggering release $containerTag for $repoName"
    boolean result = false
    result = sh(returnStdout: true, script: "curl -s -X POST -H 'Authorization: token $token' -d '{ \"tag_name\" : \"$containerTag\", \"name\" : \"Release $containerTag\", \"body\" : \" Release $releaseDescription\" }' https://api.github.com/repos/DEFRA/$repoName/releases")
    echo "The release result is $result"

    if (releaseExists(containerTag, repoName, token)){
      echo "Release Successful"
    } else {
      throw new Exception("Release failed")
    }
}

def notifySlackBuildFailure(exception, channel) {

  def msg = """BUILD FAILED
          ${JOB_NAME}/${BUILD_NUMBER}
          ${exception}
          (<${BUILD_URL}|Open>)"""

  if(branch == "master") {
    msg = '@here '.concat(msg);
    channel = "#masterbuildfailures"
  }

  slackSend channel: channel,
            color: "#ff0000",
            message: msg.replace("  ", "")
}

def versionHasIncremented(currVers, newVers) {
  try {
    currVersList = currVers.tokenize('.').collect { it.toInteger() }
    newVersList = newVers.tokenize('.').collect { it.toInteger() }
    return currVersList.size() == 3 &&
           newVersList.size() == 3 &&
           [0, 1, 2].any { newVersList[it] > currVersList[it] }
  }
  catch (Exception ex) {
    return false
  }
}

return this
