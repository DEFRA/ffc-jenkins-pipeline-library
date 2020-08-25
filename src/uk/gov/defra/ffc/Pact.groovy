package uk.gov.defra.ffc

class Provision implements Serializable {
  static def publishContractsToPactBroker(ctx, repoName, version, commitSha)
    ctx.withCredentials([
      string(credentialsId: 'pact-broker-url', variable: 'pactBrokerURL'),
      usernamePassword(credentialsId: 'pact-broker-credentials', usernameVariable: 'pactUsername', passwordVariable: 'pactPassword')
    ]) {
      ctx.dir('test-output') {
        ctx.echo "Publish pacts to broker"
        def pacts = ctx.findFiles glob: "*.json"
        ctx.echo "Found ${pacts.size()} pact file(s) to publish"
        for (pact in pacts) {
          def provider = pact.name.substring("$repoName-".length(), pact.name.indexOf(".json"))
          ctx.echo "Publishing ${pact.name} to broker"
          ctx.sh "curl -k -v -XPUT -H \"Content-Type: application/json\" --user $pactUsername:$pactPassword -d@${pact.name} $pactBrokerURL/pacts/provider/$provider/consumer/$repoName/version/$version+$commitSha"
        }
      }
    }
  }
}