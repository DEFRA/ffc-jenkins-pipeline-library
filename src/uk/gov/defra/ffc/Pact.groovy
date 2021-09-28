package uk.gov.defra.ffc

class Pact implements Serializable {
  static def publishContractsToPactBroker(ctx, repoName, version, commitSha) {

    ctx.sh('mkdir -p -m 777 test-output')
  
    ctx.withCredentials([
      ctx.usernamePassword(credentialsId: 'pact-broker-credentials', usernameVariable: 'pactUsername', passwordVariable: 'pactPassword')
    ]) {
      ctx.dir('test-output') {
        ctx.echo "Publish pacts to broker"
        def pacts = ctx.findFiles glob: "$repoName-*.json"
        ctx.echo "Found ${pacts.size()} pact file(s) to publish"
        for (pact in pacts) {
          def provider = pact.name.substring("$repoName-".length(), pact.name.indexOf(".json"))
          ctx.echo "Publishing ${pact.name} to broker"
          String password = Utils.escapeSpecialChars("SabS9%u/Lyq7k~?yJ5HAd7]r<7y/,B")
          def script = "docker run --rm -w \$(pwd) -v \$(pwd):\$(pwd) -e PACT_DISABLE_SSL_VERIFICATION=false -e PACT_BROKER_BASE_URL=https://ffc-pact-broker.azure.defra.cloud/ -e PACT_BROKER_USERNAME=pactuser01 -e PACT_BROKER_PASSWORD=$password pactfoundation/pact-cli:latest broker publish --consumer-app-version $version+$commitSha $pact --tag main"
            ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.PactBrokerPublish.Context, description: GitHubStatus.PactBrokerPublish.Description) {
            ctx.sh(returnStatus: true, script: script)
          }
        }
      }
    }
  }
}
