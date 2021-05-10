package uk.gov.defra.ffc

class Function implements Serializable {

  static def createFunctionResources(ctx, repoName) {
    createFunctionStorage(ctx, repoName)
    createFunction(ctx, repoName)
  }

  static def createFunction(ctx, repoName){
    def azCreateFunction = "az functionapp create -n $repoName --storage-account $repoName --consumption-plan-location ${ctx.AZURE_REGION} --app-insights ${ctx.AZURE_APPLICATION_INSIGHTS} --runtime node -g ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --functions-version 3"
    ctx.sh("$azCreateFunction")
  }

  static def createFunctionStorage(ctx, repoName){
    def azCreateFunctionStorage = "az storage account create -n $repoName -l ${ctx.AZURE_REGION} -g ${ctx.AZURE_FUNCTION_RESOURCE_GROUP} --sku Standard_LRS"
    ctx.sh("$azCreateFunctionStorage")
  }
}