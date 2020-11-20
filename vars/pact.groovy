import uk.gov.defra.ffc.Pact

void publishContractsToPactBroker(String repoName, String version, String commitSha) {
  Pact.publishContractsToPactBroker(this, repoName, version, commitSha)
}
