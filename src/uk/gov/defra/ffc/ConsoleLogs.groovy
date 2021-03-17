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
    //ctx.echo("dir ls")
    ctx.sh("ls")
    ctx.sh("cd ~")
    ctx.sh("cd ..")
    ctx.sh("cd ..")
    ctx.sh("ls")
    ctx.dir('../../../../../../../../../log/jenkins/console/') {
    //ctx.sh("cd /var/log/jenkins/console/")
    ctx.sh("USER root")
    ctx.sh("chown -R jenkins /var/log/jenkins/console/")
    ctx.sh("ls")

    // ctx.sh("chmod 777 /var/log/jenkins/console/")
      def script = "curl $url > log_${logName}.txt"
      ctx.echo("script: $script")
      ctx.sh(script: script, returnStdout: true)
    }
  }
}
