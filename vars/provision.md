# provision

In order to use it with the default conventions simply add the following to
your Jenkinsfile.
For the details of what happens please review the
[provision](provision.groovy) script.

## Prerequisite
For dynamic resources you will need a `provision.azure.yaml` file in your repo with the below format.
```
resources:
  queues:
    - name: claim
    - name: payment
    - name: schedule
```

```
@Library('defra-library@v-8') _

provision environment: 'dev'
```
`createResources` will create the dynamic resource queues passing in the `repoName` and `pr`

`deleteBuildResources` will delete all build dynamic resource queues leaving the pr queues to allow tests to be able to run.