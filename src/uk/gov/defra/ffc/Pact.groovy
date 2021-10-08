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

          def script = '''
            docker run --rm -w \$(pwd) -v \$(pwd):\$(pwd) -e PACT_DISABLE_SSL_VERIFICATION=false \
            -e PACT_BROKER_BASE_URL=\$PACT_BROKER_URL -e PACT_BROKER_USERNAME=$pactUsername \
            -e PACT_BROKER_PASSWORD="$pactPassword" pactfoundation/pact-cli:latest \
            broker publish --consumer-app-version \
            ''' + version + '''+''' + commitSha + ''' ''' + pact + ''' --tag main
            '''

            ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.PactBrokerPublish.Context, description: GitHubStatus.PactBrokerPublish.Description) {
            def output = ctx.sh(returnStatus: true, script: script)
            ctx.echo "output from command: $output"

            if (output == 1) {
              ctx.error("Error occurred during publishing of pacts, pleaes check the log for further details.")
            } else {
              ctx.echo("Pacts published successfully.")
            }
          }
        }
      }
    }
  }
}
