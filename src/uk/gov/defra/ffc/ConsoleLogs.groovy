package uk.gov.defra.ffc

import uk.gov.defra.ffc.Utils

class ConsoleLogs implements Serializable {
  static def save(ctx, jenkinsUrl, repoName, branch, buildNumber) {
    def folder = Utils.getFolder(ctx)
    def url = "$jenkinsUrl/job/$folder/job/$repoName-build/job/$branch/$buildNumber/consoleText"

    ctx.echo("Url: $url")

    def logName = new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))

    ctx.echo("Date: $logName")
    ctx.echo("log name: log_${logName}.txt")
    ctx.sh("cd ~")
    // ctx.sh("chmod 777 /var/log/jenkins/console/")
    def script = "$url > log_${logName}.txt"
    ctx.echo("script: $script")
    ctx.sh(script: script, returnStdout: true)
  }
}
