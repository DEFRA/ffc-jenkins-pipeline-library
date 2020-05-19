def buildFailure(exception, channel) {
  def branch = BRANCH_NAME
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

def deploymentFailure(exception) {
  def msg = """@here DEPLOYMENT FAILED
          ${JOB_NAME}/${BUILD_NUMBER}
          ${exception}
          (<${BUILD_URL}|Open>)"""

  def channel = "#masterbuildfailures"

  slackSend channel: channel,
            color: "#ff0000",
            message: msg.replace("  ", "")
}

def sendMessage(channel, message, useHere) {
  slackSend channel: channel,
            color: "#ff0000",
            message: "${useHere ? '@here ' : ''}$message"
}
