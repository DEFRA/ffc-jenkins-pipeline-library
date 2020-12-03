package uk.gov.defra.ffc

class Pact implements Serializable {
  static def publishContractsToPactBroker(ctx, repoName, version, commitSha, branchName) {

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
           
            def script = "docker run -e $ctx.PACT_BROKER_URL/pacts/provider/$provider/consumer/$repoName/version/$version+$commitSha -e $ctx.pactUsername -e $ctx.pactPassword pactfoundation/pact-cli:latest broker create-version-tag --pacticipant $pact.name --version $version+$commitSha --tag main"                                  
              ctx.gitStatusWrapper(credentialsId: 'github-token', sha: Utils.getCommitSha(ctx), repo: Utils.getRepoName(ctx), gitHubContext: GitHubStatus.PactBrokerTest.Context, description: GitHubStatus.PactBrokerTest.Description) {
              ctx.sh(returnStatus: true, script: script)
            }
          }
        }
      }

    /* ctx.withCredentials([
      ctx.usernamePassword(credentialsId: 'pact-broker-credentials', usernameVariable: 'pactUsername', passwordVariable: 'pactPassword')
    ]) {
      ctx.dir('test-output') {
        ctx.echo "Publish pacts to broker"
        def pacts = ctx.findFiles glob: "$repoName-*.json"
        ctx.echo "Found ${pacts.size()} pact file(s) to publish"
        for (pact in pacts) {
          def provider = pact.name.substring("$repoName-".length(), pact.name.indexOf(".json"))
          ctx.echo "Publishing ${pact.name} to broker"
          ctx.sh "curl -k -v -XPUT -H \"Content-Type: application/json\" --user ${ctx.pactUsername}:${ctx.pactPassword} -d@${pact.name} ${ctx.PACT_BROKER_URL}/pacts/provider/$provider/consumer/$repoName/version/$version+$commitSha/tags/$branchName"
        }
      }
    } */
  }
}
