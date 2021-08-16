package uk.gov.defra.ffc

class Function implements Serializable {

  static def createFunctionResources(ctx, repoName, pr) {
    deleteFunction(ctx, repoName, pr)
    deleteFunctionStorage(ctx, repoName, pr)
    zipFunctionResources(ctx)
    createFunctionStorage(ctx, repoName)
    createFunction(ctx, repoName, pr)
  }

  static def zipFunctionResources(ctx) {
    ctx.sh("sudo apt install zip")
    ctx.sh("zip -r deploy.zip ./*")
  }

  static def createFunction(ctx, repoName, pr){
    def storageAccountName = repoName.replace('-','').replace('ffc', '')
    def azCreateFunction = "az functionapp create -n $repoName-pr$pr --storage-account $storageAccountName --consumption-plan-location ${ctx.AZURE_REGION} --app-insights ${ctx.AZURE_FUNCTION_APPLICATION_INSIGHTS} --runtime node -g ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --functions-version 3"
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
}