package uk.gov.defra.ffc

import uk.gov.defra.ffc.Utils

class ConsoleLogs implements Serializable {
  static def save(ctx, jenkinsUrl, repoName, branch, buildNumber) {
    def folder = Utils.getFolder(ctx)
    def url = "$jenkinsUrl/job/$folder/job/$repoName-build/job/$branch/$buildNumber/consoleText"

    def logFileName = new Date().format("yyyy-MM-dd_HH:mm:ss", TimeZone.getTimeZone('UTC'))
    
    ctx.sh("[ -d /var/log/jenkins/console ]  && docker run --rm -u root --privileged --mount type=bind,source=/var/log/jenkins/console,target=/home/node defradigital/node-development chown $ctx.JENKINS_USER_ID:$ctx.JENKINS_GROUP_ID -R -v .")
   
    def script = "curl $url > /var/log/jenkins/console/log_${logFileName}.txt"
    ctx.echo("script: $script")
    ctx.sh(script: script, returnStdout: true)
  }
}
