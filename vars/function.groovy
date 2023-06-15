import uk.gov.defra.ffc.Function

void createFunctionResources(String runtime, String repoName, String pr, String gitToken, String defaultBranch) {
  Function.createFunctionResources(this, runtime, repoName, pr, gitToken, defaultBranch)
}