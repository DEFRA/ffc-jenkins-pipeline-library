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
`buildDotNetCore` and `buildNodeJs` both call the belowsignatures to allow the resources to be created and deleted at the correct times during building.

`createResources` will create the dynamic resource queues passing in the `repoName` and `pr`

`deleteBuildResources` will delete all build dynamic resource queues leaving the pr queues to allow tests to be able to run.