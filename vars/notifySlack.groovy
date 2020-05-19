import uk.gov.defra.ffc.Notifications

def buildFailure(exception, channel) {
  Notifications.buildFailure(this, exception, channel)
}

def deploymentFailure(exception) {
  Notifications.deploymentFailure(this, exception)
}

def sendMessage(channel, message, useHere) {
  Notification.sendMessage(this, channel, message, useHere)
}
