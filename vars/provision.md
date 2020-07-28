# provision

> Dynamically provision cloud resources

In order to use it with the default conventions simply add the following to
your Jenkinsfile.
For the details of what happens please review the
[provision](provision.groovy) script.

## Prerequisite
For dynamic resources you will need a `provision.azure.yaml` file in the root directory of the repo.

For queues there should be a queue name defined in `provision.azure.yaml` that will be used to name the queue. An example of this for a queue called claim is shown below:

```
resources:
  queues:
    - name: claim
```
## createResources

Creates dynamic resource queues.

The resources are creating using a manifest in the root directory of the repo which dictates the name of the resources that need setting up.

Takes two parameters:
- repository name
- pull request number

Example usage:
```
 stage('Provision resources') {
        provision.createResources(repoName, pr)
      }
```

## deleteBuildResources

Deletes dynamic resource queues

This deletes all build dynamic resource queues leaving the pr queues to allow tests to be able to run.

Takes two parameters:
- repositry name
- pull request number

Example usage:
```
 stage('Clean up resources') {
        provision.deleteBuildResources(repoName, pr)
      }
```
