package uk.gov.defra.ffc

class Terraform implements Serializable {

  static def String generateTerraformInputVariables(serviceCode, serviceName, serviceType, prCode, queuePurpose, repoName) {
    def Map inputs = [service: [code: serviceCode, name: serviceName, type: serviceType], pr_code: prCode, queue_purpose: queuePurpose, repo_name: repoName];
    return Utils.mapToString(inputs).join("\n")
  }

  private static def String[] mapToString(Map map) {
    def output = [];
    for (def item in map) {
      if (item.value instanceof String) {
        output.add("${item.key} = \"${item.value}\"");
      } else if (item.value instanceof Map) {
        output.add("${item.key} = {\n\t${Terraform.mapToString(item.value).join("\n\t")}\n}");
      } else {
        output.add("${item.key} = ${item.value}");
      }
    }
    return output;
  }
}
