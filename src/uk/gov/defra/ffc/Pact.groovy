package uk.gov.defra.ffc

class Provision implements Serializable {

def string(credentialsId,variable){
  credentialsId: 'pact-broker-url', variable: 'pactBrokerURL'
}

def usernamePassword(credentialsId, usernameVariable, passwordVariable){
  credentialsId= 'pact-broker-credentials'
  usernameVariable= 'pactUsername'
  passwordVariable= 'pactPassword'
}

def publishPacts(repoName, string, usernamePassword){
  findFiles glob: "*.json"
  echo "Found ${pacts.size()} pact file(s) to publish"
  for (publishPact in publishPacts) {
    def provider = pact.name.substring("$repoName-".length(), pact.name.indexOf(".json"))
    echo "Publishing ${pact.name} to broker"
    sh "curl -k -v -XPUT -H \"Content-Type: application/json\" --user $pactUsername:$pactPassword -d@${pact.name} $pactBrokerURL/pacts/provider/$provider/consumer/$repoName/version/$version+$commitSha"
  }
}
}