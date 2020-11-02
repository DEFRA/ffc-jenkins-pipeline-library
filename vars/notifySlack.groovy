import uk.gov.defra.ffc.Notifications

def buildFailure(channel, defaultBranch) {
  Notifications.buildFailure(this, channel, defaultBranch)
}

def deploymentFailure() {
  Notifications.deploymentFailure(this)
}

def sendMessage(channel, message, useHere) {
  Notifications.sendMessage(this, channel, message, useHere)
}
