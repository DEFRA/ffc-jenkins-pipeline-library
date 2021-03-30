package uk.gov.defra.ffc

import uk.gov.defra.ffc.Utils

class ConsoleLogs implements Serializable {
  static def save(ctx, jenkinsUrl, repoName, branch, buildNumber, logFilePath) {
    def folder = Utils.getFolder(ctx)
    def url = "$jenkinsUrl/job/$folder/job/$repoName-build/job/$branch/$buildNumber/consoleText"

    saveLogFile(url, logFilePath)
  }

  static def save(ctx, jenkinsUrl, repoName, buildNumber, logFilePath) {
    def folder = Utils.getFolder(ctx)
    def url = "$jenkinsUrl/job/$folder/job/$repoName-deploy/$buildNumber/consoleText"

    saveLogFile(url, logFilePath)
  }

  static def saveLogFile(ctx, url, logFilePath) {
    def logFileName = new Date().format("yyyy-MM-dd_HH:mm:ss", TimeZone.getTimeZone('UTC'))
    
    ctx.sh("[ -d $logFilePath ]  && docker run --rm -u root --privileged --mount type=bind,source=$logFilePath,target=/home/node defradigital/node-development chown $ctx.JENKINS_USER_ID:$ctx.JENKINS_GROUP_ID -R -v .")
   
    def script = "curl $url > $logFilePath/log_${logFileName}.txt"
    ctx.echo("script: $script")
    ctx.sh(script: script, returnStdout: true)
  }
}
