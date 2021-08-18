package uk.gov.defra.ffc

class Function implements Serializable {

  static String azureProvisionConfigFile = './provision.azure.yaml'

  static def createFunctionResources(ctx, repoName, pr, gitToken, defaultBranch) {
    if(hasResourcesToProvision(ctx, azureProvisionConfigFile)) {
      def storageAccountName = getStorageAccountName(ctx, azureProvisionConfigFile)
      //createSlots(ctx, repoName, pr)
      listFunctionApps(ctx, repoName, pr)
      deleteFunction(ctx, repoName, pr)
      deleteFunctionStorage(ctx, repoName, pr, storageAccountName)
      enableGitAuth(ctx, gitToken)
      createFunctionStorage(ctx, repoName, storageAccountName)
      createFunction(ctx, repoName, pr, defaultBranch, storageAccountName)
      //swapSlots(ctx, repoName, pr)
    }
  }
  
  static def listFunctionApps(ctx, repoName, pr) {
    def functionApps = ctx.sh(returnStdout: true, script: "az functionapp list --query '[?name==$repoName-pr$pr]'")
    ctx.echo("functionApps $functionApps")
  }

  static def createSlots(ctx, repoName, pr) {
    ctx.sh("az functionapp deployment slot create --name $repoName-pr$pr -g ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --slot staging-pr$pr")
  }

  static def swapSlots(ctx, repoName, pr) {
    ctx.sh("az functionapp deployment slot swap --name $repoName-pr$pr -g ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --slot staging-pr$pr --target-slot production")
  }

  static def getStorageAccountName(ctx, azureProvisionConfigFile) {
    def storage = readManifest(ctx, azureProvisionConfigFile, 'storage')
    validateStorageName(storage)
    return storage
  }

  static def enableGitAuth(ctx, gitToken){
    ctx.sh("az functionapp deployment source update-token --git-token $gitToken")
  }

  static def createFunction(ctx, repoName, pr, defaultBranch, storageAccountName){
    def repoUrl = Utils.getRepoUrl(ctx)
    def azCreateFunction = "az functionapp create -n $repoName-pr$pr --slot staging-pr$pr --deployment-source-url $repoUrl --deployment-source-branch $defaultBranch --storage-account $storageAccountName --consumption-plan-location ${ctx.AZURE_REGION} --app-insights ${ctx.AZURE_FUNCTION_APPLICATION_INSIGHTS} --runtime node -g ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --functions-version 3"
    ctx.sh("$azCreateFunction")
  }

  static def createFunctionStorage(ctx, repoName, storageAccountName){
    def azCreateFunctionStorage = "az storage account create -n $storageAccountName -l ${ctx.AZURE_REGION} -g ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --sku Standard_LRS"
    ctx.sh("$azCreateFunctionStorage")
  }

  private static def deleteFunction(ctx, repoName, pr) {
    def azDeleteFunction = "az functionapp delete --name $repoName-pr$pr --resource-group ${ctx.AZURE_FUNCTION_RESOURCE_GROUP}"
    ctx.sh("$azDeleteFunction")
  }

  private static def deleteFunctionStorage(ctx, repoName, pr, storageAccountName) {
    def azDeleteFunctionStorage = "az storage account delete -n $storageAccountName -g ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --yes"
    ctx.sh("$azDeleteFunctionStorage")
  }

  static def hasResourcesToProvision(ctx, filePath) {
    return ctx.fileExists(filePath)
  } 

  static def readManifest(ctx, filePath, resource) {
    def resources = ctx.sh(returnStdout: true, script: "yq r $filePath resources.${resource}.*.name").trim()
    return resources.tokenize('\n')[0]
  }

  private static def validateStorageName(name) {
    assert name ==~ /[a-z0-9]{3,24}/ : "Invalid storage name: '$name'"
  }
}