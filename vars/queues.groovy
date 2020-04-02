// private
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

// private
def String generateTerraformInputVariables(serviceCode, serviceName, serviceType, prCode, queuePurpose, repoName) {
  def Map inputs = [service: [code: serviceCode, name: serviceName, type: serviceType], pr_code: prCode, queue_purpose: queuePurpose, repo_name: repoName];
  return mapToString(inputs).join("\n")
}

// public
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

// public
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
