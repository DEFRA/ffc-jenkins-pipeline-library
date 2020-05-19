package uk.gov.defra.ffc

class Notifications implements Serializable {
  private static String color = '#ff0000'

  static def buildFailure(ctx, exception, channel) {
    def msg = """BUILD FAILED
            ${ctx.JOB_NAME}/${ctx.BUILD_NUMBER}
            ${exception}
            (<${ctx.BUILD_URL}|Open>)"""

    if(ctx.BRANCH_NAME == 'master') {
      msg = "@here ${msg}"
      channel = '#masterbuildfailures'
    }

    ctx.slackSend(channel: channel,
              color: Notifications.color,
              message: msg.replace('  ', ''))
  }

  static def deploymentFailure(ctx, exception) {
    def msg = """@here DEPLOYMENT FAILED
            ${ctx.JOB_NAME}/${ctx.BUILD_NUMBER}
            ${exception}
            (<${ctx.BUILD_URL}|Open>)"""

    ctx.slackSend(channel: '#masterbuildfailures',
              color: Notifications.color,
              message: msg.replace('  ', ''))
  }

  static def sendMessage(ctx, channel, message, useHere) {
    ctx.slackSend(channel: channel,
              color: Notifications.color,
              message: "${useHere ? '@here ' : ''}$message")
  }
}
