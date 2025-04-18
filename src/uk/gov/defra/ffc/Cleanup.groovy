package uk.gov.defra.ffc

import uk.gov.defra.ffc.Helm
import uk.gov.defra.ffc.Provision
import uk.gov.defra.ffc.Function

class Cleanup implements Serializable {
  static def prResources(ctx, environment, repoName, branchName) {
    if (repoName == '' || branchName == '') {
      ctx.echo('Unable to determine repo name and branch name, cleanup cancelled')
    } else {
      ctx.deleteDir()
      def apiParams = "state=closed&sort=closed&direction=desc&head=DEFRA:$branchName"
      def apiUrl = "https://api.github.com/repos/DEFRA/$repoName/pulls?$apiParams"
      def closedPrNo = ctx.sh(returnStdout: true, script: "curl '$apiUrl' | jq 'first | .number'").trim()
      if (closedPrNo == '' || closedPrNo == 'null') {
        ctx.echo("Could not find closed PR for branch $branchName of $repoName, cleanup cancelled")
      } else {
        ctx.echo("Tidying up kubernetes resources for PR $closedPrNo of $repoName after branch $branchName deleted")
        Helm.undeployChart(ctx, environment, repoName, "pr$closedPrNo")
        ctx.echo("Removing queues for PR $closedPrNo of $repoName after branch $branchName deleted")
        Provision.deletePrResources(ctx, environment, repoName, closedPrNo)
        ctx.echo("Removing container image for PR $closedPrNo of $repoName after branch $branchName deleted")
        Docker.deleteContainerImage(ctx, repoName, "pr$closedPrNo")
        String functionName = Function.createFunctionName(repoName, closedPrNo)
        ctx.echo("Removing storage account for PR $closedPrNo of $repoName after branch $branchName deleted")
        Function.deleteFunctionStorage(ctx, functionName)
        ctx.echo("Removing function $functionName after branch $branchName deleted")
        Function.deleteFunction(ctx, functionName)
      }
    }
  }

  static def danglingImages(ctx) {
    ctx.echo("Removing dangling images from ACR")
    Docker.deleteDanglingImages(ctx)
  }
}
