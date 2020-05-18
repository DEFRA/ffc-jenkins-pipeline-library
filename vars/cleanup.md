# Cleanup

> Methods related to cleaning up resources after branches deleted


This can be used in conjunction with the Jenkins [MultiBranch Action Triggers](https://plugins.jenkins.io/multibranch-action-triggers/) plugin to trigger a cleanup on branch deletion.

The Jenkins build job requires a `Pipeline Delete Event` configuring for each MultiBranch pipeline that needs Kubernetes cleanup. The Delete event will need an additional Parameter of `repoName`, set to the name of the github repository, and trigger another Jenkins job to perform the cleanup.

The created Jenkins job should run the following pipeline script:
```
@Library('defra-library') _

cleanup environment: 'dev'
```
