// public
def buildFailure(exception, channel) {
  echo "build message"
  def msg = """BUILD FAILED
          ${JOB_NAME}/${BUILD_NUMBER}
          ${exception}
          (<${BUILD_URL}|Open>)"""
  echo "built message: ${msg}£££"
  try {
    echo "branch name ${branch}"
  } catch (e) {
    echo "Error: ${e.message}"
  }

  if(branch == "master") {
    echo "=== 1 ==="
    msg = "@here ${msg}"
    echo "=== 2 ==="
    channel = "#masterbuildfailures"
    echo "=== 3 ==="
  }

  echo "sending to slack"
  slackSend channel: channel,
            color: "#ff0000",
            message: msg.replace("  ", "")
  echo "sent to slace"
}
