package uk.gov.defra.ffc
import groovy.transform.Field
def branch = ''
def pr = ''
def mergedPrNo = ''
def containerTag = ''
def repoUrl = ''
def commitSha = ''
def workspace

@Field def clusterRoleMappings = [ developer: 'edit', administrator: 'admin' ]

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

def destroyInfrastructure(repo_name, pr_code) {
  sshagent(['helm-chart-creds']) {
    echo "destroyInfrastructure"
    dir('terragrunt') {
      // git clone repo...
      git credentialsId: 'helm-chart-creds', url: 'git@gitlab.ffc.aws-int.defra.cloud:terraform_sqs_pipelines/terragrunt_sqs_queues.git'
      dir("london/eu-west-2/ffc") {
        def dirName = "${repo_name}-pr${pr_code}-*"
        echo "finding previous var files in directories matching ${dirName}";
        def varFiles = findFiles glob: "${dirName}/vars.tfvars";
        echo "found ${varFiles.size()} directories to tear down";
        if (varFiles.size() > 0) {
          for (varFile in varFiles) {
            def path = varFile.getPath().substring(0, varFile.getPath().lastIndexOf("/"))
            echo "running terragrunt in ${path}"
            dir(path) {
              // terragrunt destroy
              sh("terragrunt destroy -var-file='${varFile.getName()}' -auto-approve")
            }
            // delete the pr dir
            echo "removing from git"
            sh "git rm -fr ${path}"
          }
          // commit the changes back
          echo "persisting changes in repo"
          sh "git commit -m \"Removing infrastructure created for ${repo_name}#${pr_code}\" ; git push --set-upstream origin master"
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

def provisionInfrastructure(target, item, parameters) {
  echo "provisionInfrastructure"
  if (target.toLowerCase() == "aws") {
    switch (item) {
      case "sqs":
        sshagent(['helm-chart-creds']) {
          // character limit is actually 80, but four characters are needed for prefixes and separators
          final int SQS_NAME_CHAR_LIMIT = 76
          assert __hasKeys(parameters, [['service': ['code', 'name', 'type']], 'pr_code', 'queue_purpose', 'repo_name']) :
            "parameters should specify pr_code, queue_purpose, repo_name as well as service details (code, name and type)";
          assert parameters['repo_name'].size() + parameters['pr_code'].toString().size() + parameters['queue_purpose'].size() < SQS_NAME_CHAR_LIMIT :
            "repo name, pr code and queue purpose parameters should have fewer than 76 characters when combined";
          dir('terragrunt') {
            sh "pwd"
            echo "cloning terraform repo"
            // git clone repo...
            git credentialsId: 'helm-chart-creds', url: 'git@gitlab.ffc.aws-int.defra.cloud:terraform_sqs_pipelines/terragrunt_sqs_queues.git'

            dir('london/eu-west-2/ffc') {
              def dirName = "${parameters["repo_name"]}-pr${parameters["pr_code"]}-${parameters["queue_purpose"]}"
              if (!fileExists("${dirName}/terraform.tfvars")) {
                echo "${dirName} directory doesn't exist, creating..."
                echo "create new dir from model dir, then add to git"
                // create new dir from model dir, add to git...
                sh "cp -fr standard_sqs_queues ${dirName}"
                dir(dirName) {
                  echo "adding queue to git"
                  writeFile file: "vars.tfvars", text: __generateTerraformInputVariables(parameters)
                  sh "git add *.tfvars ; git commit -m \"Creating queue ${parameters["queue_purpose"]} for ${parameters["repo_name"]}#${parameters["pr_code"]}\" ; git push --set-upstream origin master"
                  echo "provision infrastructure"
                  sh "terragrunt apply -var-file='vars.tfvars' -auto-approve"
                }
              }
            }
            // Recursively delete the current dir (which should be terragrunt in the current job workspace)
            deleteDir()
          }
        }
        break;
      default:
        error("provisionInfrastructure error: unsupported item ${item}")
    }
  } else {
    error("provisionInfrastructure error: unsupported target ${target}")
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

String __getRole(environment, service, group) {
  "${environment}${service}-${group}".toUpperCase()
}

String __getRoleArn(role) {
  def accountId = sh(returnStdout: true, script: "aws sts get-caller-identity --query Account").replaceAll('"', '').trim()
  "arn:aws:iam::$accountId:role/$role"
}

String __getUser(role) {
  // Append '1' purely to differentiate from the role when looking at the logs
  "$role-1"
}

String __getRoleBindingName(role) {
 "$role-ROLEBINDING"
}

Boolean __identityMappingExists(roleArn) {
  def result = sh(
    returnStatus: true,
    script: "eksctl get iamidentitymapping --cluster $CLUSTER --arn $roleArn"
  ) as Integer
  result == 0
}

void __createIdentityMapping(role) {
  String roleArn = __getRoleArn(role)
  if (!__identityMappingExists(roleArn)) {
    String user = __getUser(role)
    sh "eksctl create iamidentitymapping --cluster $CLUSTER --arn $roleArn --username $user"
  }
}

void __deleteIdentityMapping(role) {
  def roleArn = __getRoleArn(role)
  if (__identityMappingExists(roleArn)) {
    sh "eksctl delete iamidentitymapping --cluster $CLUSTER --arn $roleArn --all"
  }
}

String __getNamespace(service, tag) {
  "$service-$tag"
}

Boolean __roleBindingExists(namespace, role) {
  def roleBindingName = __getRoleBindingName(role)
  def result = sh(
    returnStatus: true,
    script: "kubectl get rolebindings $roleBindingName --namespace $namespace"
  ) as Integer
  result == 0
}

void __createRoleBinding(namespace, role, clusterRole) {
  def roleBindingName = __getRoleBindingName(role)
  if (!__roleBindingExists(namespace, role)) {
    String user = __getUser(role)
    sh "kubectl create rolebinding $roleBindingName --user $user --clusterrole $clusterRole --namespace $namespace"
  }
}

void __deleteRoleBinding(namespace, role) {
  def roleBindingName = __getRoleBindingName(role)
  if (__roleBindingExists(namespace, role)) {
    sh "kubectl delete rolebinding $roleBindingName --namespace $namespace"
  }
}

void setupRbac(environment, service, tag) {
  String namespace = __getNamespace(service, tag)
  clusterRoleMappings.each { group, clusterRole ->
    String role = __getRole(environment, service, group)
    String user = __getUser(role)
    __createIdentityMapping(role)
    __createRoleBinding(namespace, role, clusterRole)
  }
}

void teardownRbac(environment, service, tag) {
  String namespace = __getNamespace(service, tag)
  clusterRoleMappings.each { group, clusterRole ->
     String role = __getRole(environment, service, group)
    // the identity mapping should probably only be removed after master has been torn down
    __deleteIdentityMapping(role)
    __deleteRoleBinding(namespace, role)
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

def lintHelm(chartName) {
  sh "helm lint ./helm/$chartName"
}

def buildTestImage(credentialsId, registry, projectName, buildNumber) {
  docker.withRegistry("https://$registry", credentialsId) {
    sh 'docker image prune -f || echo could not prune images'
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

def deployChart(credentialsId, registry, serviceName, tag, extraCommands) {
  def deploymentName = "$serviceName-$tag"
  echo "deploying deployment $deploymentName"
  withKubeConfig([credentialsId: credentialsId]) {
    sh "kubectl get namespaces $deploymentName || kubectl create namespace $deploymentName"
    sh "helm upgrade $deploymentName --install --atomic ./helm/$serviceName --set image=$registry/$serviceName:$tag,namespace=$deploymentName $extraCommands"
  }
}

def undeployChart(credentialsId, serviceName, tag) {
  def deploymentName = "$serviceName-$tag"
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

return this
