import uk.gov.defra.ffc.Function

void createFunctionResources(String repoName, String pr, String gitToken, String defaultBranch) {
  Function.createFunctionResources(this, repoName, pr, gitToken, defaultBranch)
}