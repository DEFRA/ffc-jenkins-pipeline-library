package uk.gov.defra.ffc

class Utils implements Serializable {
  static String suppressConsoleOutput = '#!/bin/bash +x\n'
  static String defaultNullLabel = '\\\\0'

  static def replaceInFile(ctx, from, to, file) {
    ctx.sh("sed -i -e 's/$from/$to/g' $file")
  }

  static def getCommitMessage(ctx) {
    def commitMessage = ctx.sh(returnStdout: true, script: 'git log -1 --pretty=%B | cat')

    commitMessage = escapeSpecialChars(commitMessage)

    ctx.echo('output commit messge: /' + commitMessage.replaceAll("\\r\\n|\\r|\\n", "<br />").replaceAll(/'/, /\'/) + '/')

    return commitMessage.replaceAll("\\r\\n|\\r|\\n", "<br />").replaceAll(/'/, /\'/)
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
    def errMessage = e.message
    if (!errMessage) {
      def errCauses = e.getCauses()
      if (errCauses) {
        def errMessages = []
        errCauses.each { errCause ->
          if (errCause instanceof io.snyk.jenkins.workflow.FoundIssuesCause) {
            errMessages.add(errCause.getShortDescription())
          }
        }
        errMessage = errMessages.join(', ')
      }
      errMessage = errMessage ?: 'No error message available.'
    }
    return errMessage
  }

  static def escapeSpecialChars(str) {
    return str.replace('\\', '\\\\\\\\').replace(/,/, /\,/).replace(/"/, /\"/).replace(/`/, /\`/)
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

  static def getConfigValues(ctx, searchKeys, appConfigPrefix, appConfigLabel=defaultNullLabel, escapeChars = true) {
    // The jq command in the follow assumes there is only one value per key
    // This is true ONLY if you specify a label in the az appconfig kv command
    def appConfigResults = ctx.sh(returnStdout: true, script:"$suppressConsoleOutput az appconfig kv list --subscription \$APP_CONFIG_SUBSCRIPTION --name \$APP_CONFIG_NAME --key \"*\" --label=$appConfigLabel --resolve-keyvault | jq '. | map({ (.key): .value }) | add'").trim()
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
    }

    return configValues
  }

  static def getUrlStatusCode(ctx, url) {
    return ctx.sh(returnStdout: true, script:"curl -s -w \"%{http_code}\\n\" $url -o /dev/null").trim()
  }
}
