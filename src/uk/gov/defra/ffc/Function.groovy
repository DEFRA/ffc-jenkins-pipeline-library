package uk.gov.defra.ffc

class Function implements Serializable {

  static String azureProvisionConfigFile = './provision.azure.yaml'

  static def createFunctionResources(ctx, repoName, pr, gitToken, defaultBranch) {
    if(hasResourcesToProvision(ctx, azureProvisionConfigFile)) {
      getStorageName(ctx, azureProvisionConfigFile)
      deleteFunction(ctx, repoName, pr)
      deleteFunctionStorage(ctx, repoName, pr)
      enableGitAuth(ctx, gitToken)
      createFunctionStorage(ctx, repoName)
      createFunction(ctx, repoName, pr, defaultBranch)
    }
  }

  static def getStorageName(ctx, azureProvisionConfigFile) {
    def storage = readManifest(ctx, azureProvisionConfigFile, 'storage')
    ctx.echo("Storage ${storage[0]}")
    storage.each {
      ctx.echo("Storage ${it}")
      validateStorageName(it)
    }
  }

  static def enableGitAuth(ctx, gitToken){
    ctx.sh("az functionapp deployment source update-token --git-token $gitToken")
  }

  static def createFunction(ctx, repoName, pr, defaultBranch){
    def repoUrl = Utils.getRepoUrl(ctx)
    def storageAccountName = repoName.replace('-','').replace('ffc', '')
    def azCreateFunction = "az functionapp create -n $repoName-pr$pr --deployment-source-url $repoUrl --deployment-source-branch $defaultBranch --storage-account $storageAccountName --consumption-plan-location ${ctx.AZURE_REGION} --app-insights ${ctx.AZURE_FUNCTION_APPLICATION_INSIGHTS} --runtime node -g ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --functions-version 3"
    ctx.sh("$azCreateFunction")
  }

  static def createFunctionStorage(ctx, repoName){
    def storageAccountName = repoName.replace('-','').replace('ffc', '')
    def azCreateFunctionStorage = "az storage account create -n $storageAccountName -l ${ctx.AZURE_REGION} -g ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --sku Standard_LRS"
    ctx.sh("$azCreateFunctionStorage")
  }

  private static def deleteFunction(ctx, repoName, pr) {
    def azDeleteFunction = "az functionapp delete --name $repoName-pr$pr --resource-group ${ctx.AZURE_FUNCTION_RESOURCE_GROUP}"
    ctx.sh("$azDeleteFunction")
  }

  private static def deleteFunctionStorage(ctx, repoName, pr) {
    def storageAccountName = repoName.replace('-','').replace('ffc', '')
    def azDeleteFunctionStorage = "az storage account delete -n $storageAccountName -g ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --yes"
    ctx.sh("$azDeleteFunctionStorage")
  }

  static def hasResourcesToProvision(ctx, filePath) {
    return ctx.fileExists(filePath)
  } 

  static def readManifest(ctx, filePath, resource) {
    def resources = ctx.sh(returnStdout: true, script: "yq r $filePath resources.${resource}.*.name").trim()
    return resources.tokenize('\n')
  }

  private static def validateStorageName(name) {
    assert name ==~ /[a-z0-9]{3,24}/ : "Invalid storage name: '$name'"
  }
}