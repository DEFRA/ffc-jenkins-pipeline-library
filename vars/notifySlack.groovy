// public
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
  
  channel = "#masterbuildfailures"

  slackSend channel: channel,
            color: "#ff0000",
            message: msg.replace("  ", "")
}
