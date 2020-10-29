package uk.gov.defra.ffc

class Notifications implements Serializable {
  private static String color = '#ff0000'

  static def buildFailure(ctx, channel, defaultBranch) {
    def msg = """BUILD FAILED
            ${ctx.JOB_NAME}/${ctx.BUILD_NUMBER}
            (<${ctx.BUILD_URL}|Open>)"""

    if(ctx.BRANCH_NAME == defaultBranch) {
      msg = "@here ${msg}"
      channel = '#mainbuildfailures'
    }

    ctx.slackSend(channel: channel,
              color: Notifications.color,
              message: msg.replace('  ', ''))
  }

  static def deploymentFailure(ctx) {
    def msg = """@here DEPLOYMENT FAILED
            ${ctx.JOB_NAME}/${ctx.BUILD_NUMBER}
            (<${ctx.BUILD_URL}|Open>)"""

    ctx.slackSend(channel: '#mainbuildfailures',
              color: Notifications.color,
              message: msg.replace('  ', ''))
  }

  static def sendMessage(ctx, channel, message, useHere) {
    ctx.slackSend(channel: channel,
              color: Notifications.color,
              message: "${useHere ? '@here ' : ''}$message")
  }
}
