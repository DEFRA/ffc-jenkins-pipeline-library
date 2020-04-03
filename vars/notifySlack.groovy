// public
def notifySlackBuildFailure(exception, channel) {
  def msg = """BUILD FAILED
          ${JOB_NAME}/${BUILD_NUMBER}
          ${exception}
          (<${BUILD_URL}|Open>)"""

  if(branch == "master") {
    msg = "@here ${msg}"
    channel = "#masterbuildfailures"
  }

  slackSend channel: channel,
            color: "#ff0000",
            message: msg.replace("  ", "")
}
