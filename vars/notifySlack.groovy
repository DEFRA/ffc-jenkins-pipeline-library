import uk.gov.defra.ffc.Notifications

def buildFailure(channel) {
  Notifications.buildFailure(this, channel)
}

def deploymentFailure() {
  Notifications.deploymentFailure(this)
}

def sendMessage(channel, message, useHere) {
  Notifications.sendMessage(this, channel, message, useHere)
}
