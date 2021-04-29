# Cleanup

> Methods related to cleaning up resources after branches deleted

This can be used to clean up resources in conjunction with the Jenkins
[MultiBranch Action Triggers](https://plugins.jenkins.io/multibranch-action-triggers/)
plugin which allows other pipelines to be triggered on branch events.

 Upon branch deletion for a closed PR the 
 Helm chart will be uninstalled, the PR namespace will be deleted, and queues that were created for the PR will be deleted.

To enable cleanup the MultiBranch pipeline of a service requires a
`Pipeline Delete Event` configuring.

The `Pipeline Delete Event` event should be set to a pipeline that runs the
following script:
```
@Library('defra-library') _

cleanup environment: 'snd'
```

Note: The library may be set to a specific version with the `@` suffix, e.g.
`defra-library@9`

The `Pipeline Delete Event` event will need an additional parameter of
`repoName` set to the name of the GitHub repository of the current build
pipeline. As one cleanup pipeline is shared by all build pipelines this is
required to notify the cleanup pipeline of the repository for the deleted
branch.

The cleanup function determines the PR number using the
[GitHub pulls API](https://developer.github.com/v3/pulls/). The API call
searches the repository from the `repoName` parameter for closed pull requests
on the branch that has just been deleted. The PR results are sorted by updated
date in descending order. Ordinarily only one branch is created per PR, but
this should cover the edge case when a PR is closed and a new PR is created on
the same branch.
