import uk.gov.defra.ffc.Provision

void createResources(String environment, String repoName, String tag, String pr) {
  Provision.createResources(this, environment, repoName, pr)
}

void deleteBuildResources(String repoName, String pr) {
  Provision.deleteBuildResources(this, repoName, pr)
}
