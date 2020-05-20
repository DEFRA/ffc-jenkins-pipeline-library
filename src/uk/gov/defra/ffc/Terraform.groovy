package uk.gov.defra.ffc

class Terraform implements Serializable {
  static def String generateTerraformInputVariables(serviceCode, serviceName, serviceType, prCode, queuePurpose, repoName) {
    def Map inputs = [service: [code: serviceCode, name: serviceName, type: serviceType], pr_code: prCode, queue_purpose: queuePurpose, repo_name: repoName];
    return Utils.mapToString(inputs).join('\n')
  }

  static def destroyPrSqsQueues(ctx, repoName, prCode) {
    ctx.echo('Destroy SQS Queues')
    ctx.sshagent(['helm-chart-creds']) {
      ctx.dir('terragrunt') {
        // git clone repo...
        ctx.withCredentials([
          ctx.string(credentialsId: 'ffc-jenkins-pipeline-terragrunt-repo', variable: 'tg_repo_url'),
          [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'devffcprovision-user']
        ]) {
          ctx.git(credentialsId: 'helm-chart-creds', url: ctx.tg_repo_url)
          ctx.dir('london/eu-west-2/ffc') {
            def dirName = "${repoName}-pr${prCode}-*"
            ctx.echo("finding previous var files in directories matching ${dirName}")
            def varFiles = ctx.findFiles(glob: "${dirName}/vars.tfvars")
            ctx.echo("found ${varFiles.size()} directories to tear down")
            if (varFiles.size() > 0) {
              for (def varFile in varFiles) {
                def path = varFile.getPath().substring(0, varFile.getPath().lastIndexOf("/"))
                ctx.echo("running terragrunt in ${path}")
                ctx.dir(path) {
                  // terragrunt destroy
                  ctx.sh("terragrunt destroy -var-file='${varFile.getName()}' -auto-approve --terragrunt-non-interactive")
                }
                // delete the pr dir
                ctx.echo('removing from git')
                ctx.sh("git rm -fr ${path}")
              }
              // commit the changes back
              ctx.echo('persisting changes in repo')
              ctx.sh("git commit -m \"Removing infrastructure created for ${repoName}#${prCode}\" ; git push --set-upstream origin master")
              ctx.echo('infrastructure successfully destroyed')
            } else {
              ctx.echo('no infrastructure to destroy')
            }
          }
          // Recursively delete the current dir (which should be terragrunt in the current job workspace)
          ctx.deleteDir()
        }
      }
    }
  }

  def provisionPrSqsQueue(ctx, repoName, prCode, queuePurpose, serviceCode, serviceName, serviceType) {
    ctx.echo('Provisioning SQS Queue')
    ctx.sshagent(['helm-chart-creds']) {
      // character limit is actually 80, but four characters are needed for prefixes and separators
      final int SQS_NAME_CHAR_LIMIT = 76
      assert repoName.size() + prCode.toString().size() + queuePurpose.size() < SQS_NAME_CHAR_LIMIT :
        'repo name, pr code and queue purpose parameters should have fewer than 76 characters when combined'
      ctx.echo('changing to terragrunt dir')
      ctx.dir('terragrunt') {
        ctx.echo('withCredentials')
          ctx.withCredentials([
            ctx.string(credentialsId: 'ffc-jenkins-pipeline-terragrunt-repo', variable: 'tg_repo_url'),
            [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'devffcprovision-user']
          ]) {
            ctx.sh('pwd')
            // git clone repo...
            ctx.echo('cloning repo')
            ctx.git(credentialsId: 'helm-chart-creds', url: ctx.tg_repo_url)
            ctx.echo('cloned repo')

            ctx.echo('changing to dir london/eu-west-2/ffc')
            ctx.dir('london/eu-west-2/ffc') {
            ctx.sh('pwd')
            def dirName = "${repoName}-pr${prCode}-${queuePurpose}"
            ctx.echo("checking for existing dir (${dirName})")
            if (!fileExists("${dirName}/terraform.tfvars")) {
              ctx.echo("${dirName} directory doesn't exist, creating...")
              ctx.echo('create new dir from model dir, then add to git')
              // create new dir from model dir, add to git...
              ctx.sh("cp -fr standard_sqs_queues ${dirName}")
              ctx.dir(dirName) {
                ctx.echo('adding queue to git')
                ctx.writeFile(file: 'vars.tfvars', text: Terraform.generateTerraformInputVariables(serviceCode, serviceName, serviceType, prCode, queuePurpose, repoName))
                ctx.sh("git add *.tfvars ; git commit -m \"Creating queue ${queuePurpose} for ${repoName}#${prCode}\" ; git push --set-upstream origin master")
                ctx.echo('provision infrastructure')
                ctx.sh("terragrunt apply -var-file='vars.tfvars' -auto-approve --terragrunt-non-interactive")
              }
            } else {
              ctx.echo('directory exists, presuming TG / TF has been run')
            }
          }
          // Recursively delete the current dir (which should be terragrunt in the current job workspace)
          ctx.deleteDir()
        }
      }
    }
  }

  private static def String[] mapToString(Map map) {
    def output = [];
    for (def item in map) {
      if (item.value instanceof String) {
        output.add("${item.key} = \"${item.value}\"");
      } else if (item.value instanceof Map) {
        output.add("${item.key} = {\n\t${Terraform.mapToString(item.value).join("\n\t")}\n}");
      } else {
        output.add("${item.key} = ${item.value}");
      }
    }
    return output;
  }
}
