package uk.gov.defra.ffc

class Utils implements Serializable {

  static String suppressConsoleOutput = '#!/bin/bash +x\n'
  static String defaultNullLabel = '\\\\0'
  static int appConfigReturnLimit = 1000

  static def replaceInFile(ctx, from, to, file) {
    ctx.sh("sed -i -e 's/$from/$to/g' $file")
  }

  static def getCommitMessage(ctx) {
    return ctx.sh(returnStdout: true, script: 'git log -1 --pretty=%B | cat')
  }

  static def getCommitSha(ctx) {
    return ctx.sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
  }

  static def generatePrNames(dbName, prCode) {
    def prSchema = "pr$prCode"
    def prUser = "${dbName}_$prSchema"
    return [prSchema, prUser]
  }

  static def getRepoName(ctx) {
    return ctx.scm.getUserRemoteConfigs()[0].getUrl().tokenize('/').last().split('\\.git')[0]
  }

  /**
   * Parses the local commit log to obtain the merged PR number from the message.
   * This is reliant on the standard GitHub merge message of the PR name followed by
   * the PR number, i.e. `Update license details (#53)`
   *
   * The method returns the PR number for a merge of the appropriate format, i.e.
   * `pr53` or an empty string if not.
   */
  static def getMergedPrNo(ctx) {
    def mergedPrNo = ctx.sh(returnStdout: true, script: "git log --pretty=oneline --abbrev-commit -1 | sed -n 's/.*(#\\([0-9]\\+\\)).*/\\1/p'").trim()
    return mergedPrNo ? "pr$mergedPrNo" : ''
  }

  /**
    * Obtains the remote URL of the current repository, i.e.
    * `https://github.com/DEFRA/ffc-demo-web.git`
   */
  static def getRepoUrl(ctx) {
    return ctx.sh(returnStdout: true, script: 'git config --get remote.origin.url').trim()
  }

  static def getFolder(ctx) {
    // WORKSPACE returns working directory which is /var/lib/jenkins/jobs/FOLDER/...
    // hence we want the fifth element in array when split by `/`
    def workspaceArray = ctx.WORKSPACE.split('/')
    return workspaceArray[5]
  }

  static def getErrorMessage(e) {
    return e.message ? e.message : 'No error message available'
  }

  static def escapeSpecialChars(str) {
    return str.replace('\\', '\\\\\\\\').replace(/,/, /\,/).replace(/"/, /\"/).replace(/`/, /\`/).replace(/$/, /\$/)
  }

  /**
   * Retrieves configuration values and secrets from Azure App Configuration.
   * It is intend for retrieving values that are to be used when installing
   * or updating a Helm chart. Given a list of search keys, it will generate
   * and return a map containing the values for keys found in App Configuation
   * that match the given prefix+key and label
   *
   * It takes four parameters:
   * - the Jenkins context class
   * - a list of keys to find values for
   * - any key prefix used in App Configuration
   * - the label to get values for in App Configuration, by default this is
   *   set to the default null label
   */

  static final String azureProvisionConfigFile = './provision.azure.yaml'
  static final String appConfigCommon = 'appconfig/common.yaml'
  static final String appConfigSnd = 'appconfig/snd.yaml'
  static final String subscriptionSND2 = 'dc785a20-057f-4023-b51f-f23a00f1ca2e' // AZD-FFC-SND2
  static final String subscriptionSND1 = 'cd4e9a00-99d8-45a2-98bb-7648ef12c26d' // AZD-FFC-SND1
  static final String identityPrefix = 'SNDFFCINFMID2001'

  static final Map repoNames =  [
    ffcahwrsfdmessagingproxy: 'sfdmsgprx',
    ffcahwrapplication: "ahwrapp",
    ffcdocstatementdata: "doctdt",
    ffcdocstatementpublisher: "doctdpb",
    ffcdocstatementconstructor: "doctdcon"]

  static def runAzCommand(ctx, command) {
    ctx.withCredentials([ctx.azureServicePrincipal('SSVFFCJENSR1001-Jenkins')]) {
      return ctx.sh(returnStdout: true, script:"""
      docker run --rm -v ${ctx.pwd}/jenkins-azure/.azure:/root/.azure \
      mcr.microsoft.com/azure-cli:cbl-mariner2.0 \
      $command
      """)
    }
  }

  static def getPrQueueName(repoName, pr, queueName) {
    String smalRepoName = repoName
    if (Utils.repoNames.containsKey(repoName.replaceAll('-', ''))) {
      smalRepoName = Utils.repoNames[repoName.replaceAll('-', '')]
    }
    return "$smalRepoName-pr$pr-$queueName"
  }

  static def getApplicationConfigValue(ctx, pr) {
    def configValues = [:]
    if (ctx.fileExists(azureProvisionConfigFile)) {
      def identityNameArray = ctx.sh(returnStdout: true, script: "yq r $azureProvisionConfigFile resources.identity").trim()
      def identity = identityNameArray.tokenize('\n')[0]
      def identityName = "${identityPrefix}-${identity}"
      def clientId = runAzCommand(ctx, "az identity show --resource-group $ctx.AZURE_POSTGRES_RESOURCE_GROUP_SND2 --name $identityName --query clientId --output tsv").trim()
      def resourceId = runAzCommand(ctx, "az identity show --resource-group $ctx.AZURE_POSTGRES_RESOURCE_GROUP_SND2 --name $identityName --query id --output tsv").trim()
      configValues["azureIdentity.clientID"] = clientId
      configValues["azureIdentity.resourceID"] = resourceId
      configValues["postgresService.postgresUser"] = identityName
      def postgresDbArray = ctx.sh(returnStdout: true, script: "yq r $azureProvisionConfigFile resources.postgreSql.name").trim()
      def dbs = postgresDbArray.tokenize('\n')
      if (dbs.size() > 0 && dbs[0] != "") {
        def postgresDb = dbs[0]
        configValues["postgresService.postgresDb"] = "$postgresDb-snd"
      }
    }
    def repoName = getRepoName(ctx)
    if (ctx.fileExists(appConfigCommon)) {
      def configs = ctx.readYaml file: appConfigCommon
      configs.each { k, v ->
        if (k != "") {
          def value = v
          if (value.startsWith("queue:")) {
            value = value.split(":")[1]
            if (pr != "") {
              value = getPrQueueName(repoName, pr, value)
            }
          }
          configValues[k] = value
        }
      }
    }
    if (ctx.fileExists(appConfigSnd)) {
      def configs = ctx.readYaml file: appConfigSnd
      configs.each { k, v ->
        if (k != "") {
          def value = v
          if (value.startsWith("queue:")) {
            value = value.split(":")[1]
            if (pr != "") {
              value = getPrQueueName(repoName, pr, value)
            }
          }
          configValues[k] = value
        }
      }
    }
    return configValues
  }

  static def getConfigValues(ctx, searchKeys, appConfigPrefix, appConfigLabel=defaultNullLabel, escapeChars = true) {
    // The jq command in the follow assumes there is only one value per key
    // This is true ONLY if you specify a label in the az appconfig kv command
    def appConfigResults = ctx.sh(returnStdout: true, script:"$suppressConsoleOutput az appconfig kv list --subscription \$APP_CONFIG_SUBSCRIPTION --name \$APP_CONFIG_NAME --key \"$appConfigPrefix*\" --label=$appConfigLabel --top $appConfigReturnLimit --resolve-keyvault | jq '. | map({ (.key): .value }) | add'").trim()
    def appConfigMap = ctx.readJSON([text: appConfigResults, returnPojo: true]) ?: [:]
    def configValues = [:]

    searchKeys.each { key ->
      // We can't use a GString here as the map keys are plain Java strings, so the containsKey won't match a GString
      def searchKey = appConfigPrefix + key

      if (appConfigMap.containsKey(searchKey)) {
        configValues[key] = escapeChars ? $/"${escapeSpecialChars(appConfigMap[searchKey])}"/$ : appConfigMap[searchKey]
      }
    }

    if (configValues.size() > 0) {
      ctx.echo("Following keys found with prefix=$appConfigPrefix and label=$appConfigLabel: ${configValues.keySet()}")
    } else {
      ctx.echo("No keys were found with prefix=$appConfigPrefix and label=$appConfigLabel. If you were expecting values for this configuration, check that 'environment', 'name', 'namespace', 'workstream' & 'image' are correct in the project's 'values.yaml'.")
    }

    return configValues
  }

  static def getUrlStatusCode(ctx, url) {
    return ctx.sh(returnStdout: true, script:"curl -s -w \"%{http_code}\\n\" $url -o /dev/null").trim()
  }

  static def sendNotification(ctx, channel, msg, color) {
    ctx.withCredentials([ctx.string(credentialsId: "slack-$channel-channel-webhook", variable: 'webHook')
    ]) {
      def script = "docker run -e SLACK_WEBHOOK=$ctx.webHook -e SLACK_MESSAGE=$msg -e SLACK_COLOR=$color technosophos/slack-notify:latest"
      ctx.sh(returnStatus: true, script: script)
    }
  }

  static Boolean checkCredentialsExist(ctx, id) {
    try {
      ctx.withCredentials([ctx.string(credentialsId: id, variable: 'jenkinsToken')]) {
        true
      }
    } catch (_) {
      false
    }
  }

  static String sanitizeTag(String tag) {
    return tag.replace(".", "p")
  }

}
