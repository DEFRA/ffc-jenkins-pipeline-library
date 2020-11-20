import uk.gov.defra.ffc.Notifications

void buildFailure(String channel, String defaultBranch) {
  Notifications.buildFailure(this, channel, defaultBranch)
}

void deploymentFailure() {
  Notifications.deploymentFailure(this)
}

void sendMessage(String channel, String message, Boolean useHere) {
  Notifications.sendMessage(this, channel, message, useHere)
}
