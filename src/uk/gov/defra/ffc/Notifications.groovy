package uk.gov.defra.ffc

import uk.gov.defra.ffc.Utils

class Notifications implements Serializable {
  private static String color = '#ff0000'

  static def buildFailure(ctx, channel, defaultBranch) {
    def msg = "BUILD FAILED \r\n${ctx.JOB_NAME}/${ctx.BUILD_NUMBER} \r\n(<${ctx.BUILD_URL}|Open>)"

    if(ctx.BRANCH_NAME == defaultBranch) {
      msg = "<!here> ${msg}"
      channel = '#mainbuildfailures'
    }

    Utils.sendNotification(ctx, channel, "\'$msg\'", color)
  }

  static def deploymentFailure(ctx) {
    def msg = "\'<!here> DEPLOYMENT FAILED \r\n${ctx.JOB_NAME}/${ctx.BUILD_NUMBER} \r\n(<${ctx.BUILD_URL}|Open>)\'"

    Utils.sendNotification(ctx, '#mainbuildfailures', msg, color)
  }

  static def sendMessage(ctx, channel, message, useHere) {

    Utils.sendNotification(ctx, channel, "\'${useHere ? '<!here> ' : ''}$message\'", color)    
  }
}
