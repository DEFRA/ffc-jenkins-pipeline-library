package uk.gov.defra.ffc

class Function implements Serializable {

  static def createFunctionResources(ctx, repoName, pr) {
    deleteFunctionResources(ctx, repoName, pr)
    createFunctionStorage(ctx, repoName, pr)
    createFunction(ctx, repoName, pr)
  }

  static def createFunction(ctx, repoName){
    def storageAccountName = repoName.replace('-','').replace('ffc', '')
    def azCreateFunction = "az functionapp create -n $repoName-pr$pr --storage-account $storageAccountName --consumption-plan-location ${ctx.AZURE_REGION} --app-insights ${ctx.AZURE_FUNCTION_APPLICATION_INSIGHTS} --runtime node -g ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --functions-version 3"
    ctx.sh("$azCreateFunction")
  }

  static def createFunctionStorage(ctx, repoName){
    def storageAccountName = repoName.replace('-','').replace('ffc', '')
    def azCreateFunctionStorage = "az storage account create -n $storageAccountName -l ${ctx.AZURE_REGION} -g ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --sku Standard_LRS"
    ctx.sh("$azCreateFunctionStorage")
  }

  private static def deleteFunctionResources(ctx, repoName) {
    def azDeleteFunction = "az functionapp delete --name $repoName-pr$pr --resource-group ${ctx.AZURE_FUNCTION_RESOURCE_GROUP}"
    ctx.sh("$azDeleteFunction")
  }
}