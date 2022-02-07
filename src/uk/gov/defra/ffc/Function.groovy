package uk.gov.defra.ffc

class Function implements Serializable {

  static String azureProvisionConfigFile = './provision.azure.yaml'
  static String azureFunctionConfigFile = './function.azure.yaml'

  static String createFunctionName(repoName, pr) {
    if (pr != '') {
      return "$repoName-pr$pr"
    }

    return "$repoName"
  }

  static def createFunctionResources(ctx, repoName, pr, gitToken, branch) {
    if(hasResourcesToProvision(ctx, azureProvisionConfigFile) && hasResourcesToProvision(ctx, azureFunctionConfigFile)) {
      
      def functionName = createFunctionName(repoName, pr)

      if(!checkFunctionAppExists(ctx, functionName)) {
        def storageAccountName = getStorageAccountName(ctx, azureProvisionConfigFile, pr)
        createFunctionStorage(ctx, storageAccountName)
        createFunction(ctx, functionName, branch, storageAccountName)
      }

      enableGitAuth(ctx, gitToken)
      deployFunction(ctx, functionName, branch, gitToken)
      setFunctionAppSettings(ctx, functionName)
    }
  }
  
  static Boolean checkFunctionAppExists(ctx, functionName) {
    def functionApps = ctx.sh(returnStdout: true, script: "az functionapp list --query '[].{Name:name}'")
    def checkExists = functionApps.contains("$functionName")
    ctx.echo("Function app $functionName exists: $checkExists")
    return checkExists
  }

  static def getStorageAccountName(ctx, azureProvisionConfigFile, pr) {
    def storage = readManifest(ctx, azureProvisionConfigFile, 'resources', 'storage')
    
    if (pr != '') {
      storage = "$storage-pr$pr"
    }

    validateStorageName(storage)
    return storage
  }

  static def enableGitAuth(ctx, gitToken){
    ctx.sh("az functionapp deployment source update-token --git-token $gitToken")
  }

  static def createFunction(ctx, functionName, defaultBranch, storageAccountName){
    def azCreateFunction = "az functionapp create -n $functionName --storage-account $storageAccountName --consumption-plan-location ${ctx.AZURE_REGION} --app-insights ${ctx.AZURE_FUNCTION_APPLICATION_INSIGHTS} --runtime node -g ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --functions-version 3"
    ctx.sh("$azCreateFunction")
  }

  static def createFunctionStorage(ctx, storageAccountName){
    def azCreateFunctionStorage = "az storage account create -n $storageAccountName -l ${ctx.AZURE_REGION} -g ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --sku Standard_LRS"
    ctx.sh("$azCreateFunctionStorage")
  }

  static def deployFunction(ctx, functionName, branch, gitToken){
    def repoUrl = Utils.getRepoUrl(ctx)
    def azDeployFunction = "az functionapp deployment source config --git-token $gitToken --name $functionName --resource-group ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --repo-url $repoUrl --branch $branch --manual-integration"
    ctx.sh("$azDeployFunction")
  }

  private static def deleteFunction(ctx, functionName) {
    def azDeleteFunction = "az functionapp delete --name $functionName --resource-group ${ctx.AZURE_FUNCTION_RESOURCE_GROUP}"
    ctx.sh("$azDeleteFunction")
  }

  private static def deleteFunctionStorage(ctx, repoName, pr, storageAccountName) {
    def azDeleteFunctionStorage = "az storage account delete -n $storageAccountName -g ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --yes"
    ctx.sh("$azDeleteFunctionStorage")
  }

  static def hasResourcesToProvision(ctx, filePath) {
    return ctx.fileExists(filePath)
  } 

  static def readSettings(ctx, filePath, root, resource) {
    def resources = ctx.sh(returnStdout: true, script: "yq -o=json $filePath").trim()
    ctx.echo("resources: ${resources}")
  }

  static def readManifest(ctx, filePath, root, resource) {
    def resources = ctx.sh(returnStdout: true, script: "yq r $filePath ${root}.${resource}.*").trim()
    ctx.echo("resources: ${resources}")
    return resources.tokenize('\n')[0]
  }

  static def setFunctionAppSettings(ctx, functionName) {
    readSettings(ctx, azureFunctionConfigFile, 'settings', 'values')
    ctx.echo("settings: ${settings}")
    ctx.sh("az functionapp config appsettings set --name $functionName --resource-group ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --settings 'testAppSettings=test-storage' --settings 'testAppSettings2=test-storage'")
  }

  private static def validateStorageName(name) {
    assert name ==~ /[a-z0-9]{3,24}/ : "Invalid storage name: '$name'"
  }
}