// public
def buildFailure(exception, channel) {
  echo "build message"
  def msg = """BUILD FAILED
          ${JOB_NAME}/${BUILD_NUMBER}
          ${exception}
          (<${BUILD_URL}|Open>)"""
  echo "built message: ${msg}"

  if(branch == "master") {
    msg = "@here ${msg}"
    channel = "#masterbuildfailures"
  }

  echo "sending to slack"
  slackSend channel: channel,
            color: "#ff0000",
            message: msg.replace("  ", "")
  echo "sent to slace"
}
