import uk.gov.defra.ffc.Terraform

def destroyPrSqsQueues(repoName, prCode) {
  Terraform.destroyPrSqsQueues(this, repoName, prCode)
}

def provisionPrSqsQueue(repoName, prCode, queuePurpose, serviceCode, serviceName, serviceType) {
  Terraform.provisionPrSqsQueue(this, repoName, prCode, queuePurpose, serviceCode, serviceName, serviceType)
}
