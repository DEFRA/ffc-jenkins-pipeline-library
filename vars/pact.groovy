import uk.gov.defra.ffc.Pact

void publishContractsToPactBroker(String repoName, String version, String commitSha, String branchName) {
  Pact.publishContractsToPactBroker(this, repoName, version, commitSha, branchName)
}
