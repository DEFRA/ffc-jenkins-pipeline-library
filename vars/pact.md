# pact

> Publishing to Pact Broker

In order to use it with the default conventions simply add the following to
your Jenkinsfile.
For the details of what happens please review the
[pact](pact.groovy) script.

## publishContractsToPactBroker

Publishes contract to the pact broker.

Takes three parameters:
- repository name
- version number
- commitSha

Example usage:
```
pact.publishContractsToPactBroker(repoName, version.getPackageJsonVersion(), utils.getCommitSha())
```
