import uk.gov.defra.ffc.Pact

def string(credentialsId,variable){
  Pact.string(this, credentialsId, variable)
}

def usernamePassword(credentialsId, usernameVariable, passwordVariable){
  Pact.usernamePassword(this, credentialsId, usernameVariable, passwordVariable)
}

def pacts(repoName, string, usernamePassword){
  findFiles glob: "*.json"
  echo "Found ${pacts.size()} pact file(s) to publish"
  for (pact in pacts) {
    def provider = pact.name.substring("$repoName-".length(), pact.name.indexOf(".json"))
    echo "Publishing ${pact.name} to broker"
    sh "curl -k -v -XPUT -H \"Content-Type: application/json\" --user $pactUsername:$pactPassword -d@${pact.name} $pactBrokerURL/pacts/provider/$provider/consumer/$repoName/version/$version+$commitSha"
  }
}

