import uk.gov.defra.ffc.Notifications

def buildFailure(String channel, String defaultBranch) {
  Notifications.buildFailure(this, channel, defaultBranch)
}

def deploymentFailure() {
  Notifications.deploymentFailure(this)
}

def sendMessage(String channel, String message, Boolean useHere) {
  Notifications.sendMessage(this, channel, message, useHere)
}
