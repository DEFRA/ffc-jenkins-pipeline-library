// public
def buildFailure(exception, channel) {
  def branch = BRANCH_NAME
  echo "build message"
  def msg = """BUILD FAILED
          ${JOB_NAME}/${BUILD_NUMBER}
          ${exception}
          (<${BUILD_URL}|Open>)"""
  echo "built message: ${msg}£££"

  if(branch == "master") {
    echo "=== 3 ==="
    msg = "@here ${msg}"
    echo "=== 4 ==="
    channel = "#masterbuildfailures"
    echo "=== 5 ==="
  }

  echo "sending to slack"
  slackSend channel: channel,
            color: "#ff0000",
            message: msg.replace("  ", "")
  echo "sent to slace"
}
