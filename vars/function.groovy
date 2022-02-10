import uk.gov.defra.ffc.Function

void createFunctionResources(String environment, String repoName, String pr, String gitToken, String defaultBranch) {
  Function.createFunctionResources(this, environment, repoName, pr, gitToken, defaultBranch)
}