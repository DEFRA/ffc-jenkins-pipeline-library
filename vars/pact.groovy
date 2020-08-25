import uk.gov.defra.ffc.Pact

def publishContractsToPactBroker(repoName, version, commitSha) {
  Pact.publishContractsToPactBroker(this, repoName, version, commitSha)
}

