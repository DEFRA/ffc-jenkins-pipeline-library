package uk.gov.defra.ffc
def branch = ''
def pr = ''
def mergedPrNo = ''
def containerTag = ''
def repoUrl = ''
def commitSha = ''
def workspace

def String[] mapToString(Map map) {
    def output = [];
    for (item in map) {
        if (item.value instanceof String) {
            output.add("${item.key} = \"${item.value}\"");
        } else if (item.value instanceof Map) {
            output.add("${item.key} = {\n\t${mapToString(item.value).join("\n\t")}\n}");
        } else {
            output.add("${item.key} = ${item.value}");
        }
    }
    return output;
}

def String generateTerraformInputVariables(serviceCode, serviceName, serviceType, prCode, queuePurpose, repoName) {
  def Map inputs = [service: [code: serviceCode, name: serviceName, type: serviceType], pr_code: prCode, queue_purpose: queuePurpose, repo_name: repoName];
  return mapToString(inputs).join("\n")
}

def destroyPrSqsQueues(repoName, prCode) {
  echo "Destroy SQS Queues"
  sshagent(['helm-chart-creds']) {
    dir('terragrunt') {
      // git clone repo...
      withCredentials([
        string(credentialsId: 'ffc-jenkins-pipeline-terragrunt-repo', variable: 'tg_repo_url'),
        [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'devffcprovision-user']
      ]) {
        git credentialsId: 'helm-chart-creds', url: tg_repo_url
        dir("london/eu-west-2/ffc") {
          def dirName = "${repoName}-pr${prCode}-*"
          echo "finding previous var files in directories matching ${dirName}";
          def varFiles = findFiles glob: "${dirName}/vars.tfvars";
          echo "found ${varFiles.size()} directories to tear down";
          if (varFiles.size() > 0) {
            for (varFile in varFiles) {
              def path = varFile.getPath().substring(0, varFile.getPath().lastIndexOf("/"))
              echo "running terragrunt in ${path}"
              dir(path) {
                // terragrunt destroy
                sh("terragrunt destroy -var-file='${varFile.getName()}' -auto-approve --terragrunt-non-interactive")
              }
              // delete the pr dir
              echo "removing from git"
              sh "git rm -fr ${path}"
            }
            // commit the changes back
            echo "persisting changes in repo"
            sh "git commit -m \"Removing infrastructure created for ${repoName}#${prCode}\" ; git push --set-upstream origin master"
            echo "infrastructure successfully destroyed"
          } else {
            echo "no infrastructure to destroy"
          }
        }
        // Recursively delete the current dir (which should be terragrunt in the current job workspace)
        deleteDir()
      }
    }
  }
}

def provisionPrSqsQueue(repoName, prCode, queuePurpose, serviceCode, serviceName, serviceType) {
  echo "Provisioning SQS Queue"
  sshagent(['helm-chart-creds']) {
    // character limit is actually 80, but four characters are needed for prefixes and separators
    final int SQS_NAME_CHAR_LIMIT = 76
    assert repoName.size() + prCode.toString().size() + queuePurpose.size() < SQS_NAME_CHAR_LIMIT :
      "repo name, pr code and queue purpose parameters should have fewer than 76 characters when combined";
    echo "changing to terragrunt dir"
    dir('terragrunt') {
      echo "withCredentials"
       withCredentials([
        string(credentialsId: 'ffc-jenkins-pipeline-terragrunt-repo', variable: 'tg_repo_url'),
        [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'devffcprovision-user']
      ]) {
        sh "pwd"
        // git clone repo...
        echo "cloning repo"
        git credentialsId: 'helm-chart-creds', url: tg_repo_url
        echo "cloned repo"

        echo "changing to dir london/eu-west-2/ffc"
        dir('london/eu-west-2/ffc') {
          sh "pwd"
          def dirName = "${repoName}-pr${prCode}-${queuePurpose}"
          echo "checking for existing dir (${dirName})"
          if (!fileExists("${dirName}/terraform.tfvars")) {
            echo "${dirName} directory doesn't exist, creating..."
            echo "create new dir from model dir, then add to git"
            // create new dir from model dir, add to git...
            sh "cp -fr standard_sqs_queues ${dirName}"
            dir(dirName) {
              echo "adding queue to git"
              writeFile file: "vars.tfvars", text: generateTerraformInputVariables(serviceCode, serviceName, serviceType, prCode, queuePurpose, repoName)
              sh "git add *.tfvars ; git commit -m \"Creating queue ${queuePurpose} for ${repoName}#${prCode}\" ; git push --set-upstream origin master"
              echo "provision infrastructure"
              sh "terragrunt apply -var-file='vars.tfvars' -auto-approve --terragrunt-non-interactive"
            }
          } else {
            echo "directory exists, presuming TG / TF has been run"
          }
        }
        // Recursively delete the current dir (which should be terragrunt in the current job workspace)
        deleteDir()
      }
    }
  }
}

def generatePrNames(dbName, prCode) {
  def prSchema = "pr$prCode"
  def prUser = "${dbName}_$prSchema"
  return [prSchema, prUser]
}

def runPsqlCommand(dbHost, dbUser, dbName, sqlCmd) {
  sh returnStdout: true, script: "psql --host=$dbHost --username=$dbUser --dbname=$dbName --no-password --command=\"$sqlCmd;\""
}

// The design rationale for the behaviour of this function is documented here:
// https://eaflood.atlassian.net/wiki/spaces/FPS/pages/1596653973/Creating+PR+database+namespaces+in+postgres
def provisionPrDatabaseRoleAndSchema(host, dbName, jenkinsUserCredId, prUserCredId, prCode, useIfNotExists=false) {
  withCredentials([
    usernamePassword(credentialsId: jenkinsUserCredId, usernameVariable: 'dbUser', passwordVariable: 'PGPASSWORD'),
    string(credentialsId: host, variable: 'dbHost'),
    usernamePassword(credentialsId: prUserCredId, usernameVariable: 'ignore', passwordVariable: 'prUserPassword'),
  ]) {
    (prSchema, prUser) = generatePrNames(dbName, prCode)
    def roleExists = false

    // CREATE ROLE doesn't have a "IF NOT EXISTS" parameter so we have to check for the PR user/role manually
    if (useIfNotExists) {
      def selectRoleSqlCmd = "SELECT 1 FROM pg_roles WHERE rolname = '$prUser'"
      roleExists = runPsqlCommand(dbHost, dbUser, dbName, selectRoleSqlCmd).contains("(1 row)")
    }

    if (roleExists) {
      echo "Role $prUser already exists, skipping"
    }
    else {
      def createRoleSqlCmd = "CREATE ROLE $prUser PASSWORD '$prUserPassword' NOSUPERUSER NOCREATEDB CREATEROLE INHERIT LOGIN"
      runPsqlCommand(dbHost, dbUser, dbName, createRoleSqlCmd)
    }

    def ifNotExistsStr = useIfNotExists ? "IF NOT EXISTS" : ""
    def createSchemaSqlCmd = "CREATE SCHEMA $ifNotExistsStr $prSchema"
    runPsqlCommand(dbHost, dbUser, dbName, createSchemaSqlCmd)

    def grantPrivilegesSqlCmd = "GRANT ALL PRIVILEGES ON SCHEMA $prSchema TO $prUser"
    runPsqlCommand(dbHost, dbUser, dbName, grantPrivilegesSqlCmd)
  }

  return generatePrNames(dbName, prCode)
}

// The design rationale for the behaviour of this function is documented here:
// https://eaflood.atlassian.net/wiki/spaces/FPS/pages/1596653973/Creating+PR+database+namespaces+in+postgres
def destroyPrDatabaseRoleAndSchema(host, dbName, jenkinsUserCredId, prCode) {
  withCredentials([
    usernamePassword(credentialsId: jenkinsUserCredId, usernameVariable: 'dbUser', passwordVariable: 'PGPASSWORD'),
    string(credentialsId: host, variable: 'dbHost'),
  ]) {
    (prSchema, prUser) = generatePrNames(dbName, prCode)

    def dropSchemaSqlCmd = "DROP SCHEMA IF EXISTS $prSchema CASCADE"
    runPsqlCommand(dbHost, dbUser, dbName, dropSchemaSqlCmd)

    def dropRoleSqlCmd = "DROP ROLE IF EXISTS $prUser"
    runPsqlCommand(dbHost, dbUser, dbName, dropRoleSqlCmd)
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

def getFileVersion(fileName) {
  return sh(returnStdout: true, script: "cat ${fileName}").trim()
}

def getFileVersionMaster(fileName) {
  return sh(returnStdout: true, script: "git show origin/master:${fileName}").trim()
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

def verifyFileVersionIncremented(fileName) {
  def masterVersion = getFileVersionMaster(fileName)
  def version = getFileVersion(fileName)
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

def lintHelm(chartName) {
  sh "helm lint ./helm/$chartName"
}

def buildTestImage(credentialsId, registry, projectName, buildNumber) {
  docker.withRegistry("https://$registry", credentialsId) {
    sh "docker-compose -p $projectName-$containerTag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml build --no-cache"
  }
}

def runTests(projectName, serviceName, buildNumber) {
  try {
    sh 'mkdir -p test-output'
    sh 'chmod 777 test-output'
    sh "docker-compose -p $projectName-$containerTag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml run $serviceName"
  } finally {
    sh "docker-compose -p $projectName-$containerTag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml down -v"
  }
}

def createTestReportJUnit(){
  junit 'test-output/junit.xml'
}

def deleteTestOutput(containerImage, containerWorkDir) {
    // clean up files created by node/ubuntu user that cannot be deleted by jenkins. Note: uses global environment variable
    sh "[ -d \"$WORKSPACE/test-output\" ] && docker run --rm -u node --mount type=bind,source='$WORKSPACE/test-output',target=/$containerWorkDir/test-output $containerImage rm -rf test-output/*"
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
    sh "docker-compose -f docker-compose.yaml build --no-cache"
    sh "docker tag $imageName $registry/$imageName:$tag"
    sh "docker push $registry/$imageName:$tag"
  }
}

def deployChart(credentialsId, registry, chartName, tag, extraCommands) {
  withKubeConfig([credentialsId: credentialsId]) {
    def deploymentName = "$chartName-$tag"
    sh "kubectl get namespaces $deploymentName || kubectl create namespace $deploymentName"
    sh "helm upgrade $deploymentName --install --atomic ./helm/$chartName --set image=$registry/$chartName:$tag,namespace=$deploymentName $extraCommands"
  }
}

def undeployChart(credentialsId, chartName, tag) {
  def deploymentName = "$chartName-$tag"
  echo "removing deployment $deploymentName"
  withKubeConfig([credentialsId: credentialsId]) {
    sh "helm delete --purge $deploymentName || echo error removing deployment $deploymentName"
    sh "kubectl delete namespaces $deploymentName || echo error removing namespace $deploymentName"
  }
}

def publishChart(registry, chartName, tag) {
  withCredentials([
    string(credentialsId: 'helm-chart-repo', variable: 'helmRepo')
  ]) {
    // jenkins doesn't tidy up folder, remove old charts before running
    sh "rm -rf helm-charts"
    sshagent(credentials: ['helm-chart-creds']) {
      sh "git clone $helmRepo"
      dir('helm-charts') {
        sh 'helm init -c'
        sh "sed -i -e 's/image: .*/image: $registry\\/$chartName:$tag/' ../helm/$chartName/values.yaml"
        sh "sed -i -e 's/version:.*/version: $tag/' ../helm/$chartName/Chart.yaml"
        sh "helm package ../helm/$chartName"
        sh 'helm repo index .'
        sh 'git config --global user.email "buildserver@defra.gov.uk"'
        sh 'git config --global user.name "buildserver"'
        sh 'git checkout master'
        sh 'git add -A'
        sh "git commit -m 'update $chartName helm chart from build job'"
        sh 'git push'
      }
    }
  }
}

def deployRemoteChart(namespace, chartName, chartVersion, extraCommands) {
  withKubeConfig([credentialsId: KUBE_CREDENTIALS_ID]) {
    sh "helm repo add ffc-demo $HELM_CHART_REPO"
    sh "helm repo update"
    sh "helm fetch --untar ffc-demo/$chartName --version $chartVersion"
    sh "helm upgrade --install --recreate-pods --wait --atomic $chartName --set namespace=$namespace ./$chartName $extraCommands"
  }
}

def triggerDeploy(jenkinsUrl, jobName, token, params) {
  def url = "$jenkinsUrl/job/$jobName/buildWithParameters?token=$token"
  params.each { param ->
    url = url + "\\&$param.key=$param.value"
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

def attachTag(tag, commitSha, credentialsId) {
  sshagent(['github-test']) {
    sh("git push origin :refs/tags/$tag")
    sh("git tag -f $tag $commitSha")
    sh("git push origin $tag")
  }
}

def tagCommit(version, gitToken) {
  def versionList = version.tokenize('.')
  assert versionList.size() == 3

  def majorTag = "${versionList[0]}"
  def minorTag = "${versionList[0]}.${versionList[1]}"
  def commitSha = getCommitSha()

  attachTag(minorTag, commitSha, gitToken)
}

return this
