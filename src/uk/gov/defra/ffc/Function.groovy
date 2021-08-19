package uk.gov.defra.ffc

class Function implements Serializable {

  static String azureProvisionConfigFile = './provision.azure.yaml'

  static def createFunctionResources(ctx, repoName, pr, gitToken, branch) {
    if(hasResourcesToProvision(ctx, azureProvisionConfigFile)) {
      def storageAccountName = getStorageAccountName(ctx, azureProvisionConfigFile)

      if(!checkFunctionAppExists(ctx, repoName, pr)) {
        createFunctionStorage(ctx, repoName, storageAccountName)
        createFunction(ctx, repoName, pr, branch, storageAccountName)
      }

      enableGitAuth(ctx, gitToken)
      deployFunction(ctx, repoName, pr, branch, gitToken)
    }
  }
  
  static Boolean checkFunctionAppExists(ctx, repoName, pr) {
    def functionApps = ctx.sh(returnStdout: true, script: "az functionapp list --query '[].{Name:name}'")
    def checkExists = functionApps.contains("$repoName-pr$pr")
    ctx.echo("Function app $repoName-pr$pr exists: $checkExists")
    return checkExists
  }

  static def getStorageAccountName(ctx, azureProvisionConfigFile) {
    def storage = readManifest(ctx, azureProvisionConfigFile, 'storage')
    validateStorageName(storage)
    return storage
  }

  static def enableGitAuth(ctx, gitToken){
    ctx.sh("az functionapp deployment source update-token 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 $gitToken")
  }

  static def createFunction(ctx, repoName, pr, defaultBranch, storageAccountName){
    def azCreateFunction = "az functionapp create -n $repoName-pr$pr --storage-account $storageAccountName --consumption-plan-location ${ctx.AZURE_REGION} --app-insights ${ctx.AZURE_FUNCTION_APPLICATION_INSIGHTS} --runtime node -g ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --functions-version 3"
    ctx.sh("$azCreateFunction")
  }

  static def createAndDeployFunction(ctx, repoName, pr, defaultBranch, storageAccountName){
    def repoUrl = Utils.getRepoUrl(ctx)
    def azCreateFunction = "az functionapp create -n $repoName-pr$pr --deployment-source-url $repoUrl --deployment-source-branch $defaultBranch --storage-account $storageAccountName --consumption-plan-location ${ctx.AZURE_REGION} --app-insights ${ctx.AZURE_FUNCTION_APPLICATION_INSIGHTS} --runtime node -g ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --functions-version 3"
    ctx.sh("$azCreateFunction")
  }

  static def createFunctionStorage(ctx, repoName, storageAccountName){
    def azCreateFunctionStorage = "az storage account create -n $storageAccountName -l ${ctx.AZURE_REGION} -g ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --sku Standard_LRS"
    ctx.sh("$azCreateFunctionStorage")
  }

  static def deployFunction(ctx, repoName, pr, branch, gitToken){
    def repoUrl = Utils.getRepoUrl(ctx)
    def azDeployFunction = "az functionapp deployment source config --git-token $gitToken --name $repoName-pr$pr --resource-group ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --repo-url $repoUrl --branch $branch"
    ctx.sh("$azDeployFunction")
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