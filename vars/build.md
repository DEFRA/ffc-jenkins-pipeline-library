### getMergedPrNo

Parses the local commit log to obtain the merged PR number from the message. This is reliant on the standard github merge message of the PR name followed by the PR number, i.e.
```
Update license details (#53)
```

The method returns the PR number for a merge of the appropriate format, i.e. `pr53` or an empty string if not.

### getRepoUrl

Obtains the remote URL of the current repository, i.e. `https://github.com/DEFRA/ffc-demo-web.git`

### getCommitSha

Return the SHA hash of the latest commit for the current repository, i.e. `9fd0a77d3eaa3d4370d3f31158f37dd8abd19fae`

### verifyCommitBuildable

If the build is a branch with a Pull Request (PR), or the master branch a message will be `echoed` describing the type of build.

If the build is not for a PR or the master branch an error will be thrown with the message `Build aborted - not a PR or a master branch`

### getVariables (Obsolete, see build.getVariables, below)

Takes the repository name as a parameter, i.e. `ffc-demo-web`, as well as the relevant version parameter to call depending on the project type.

It returns information required by the build steps as an array
- the PR number, i.e. `53`
- the container tag, either the SemVer number (for master branch) or the PR number prefixed with pr, i.e. `pr53`
- the merged PR number

Example usage:

```
(pr, containerTag, mergedPrNo) = defraUtils.getVariables('ffc-demo-payment-service', defraUtils.getPackageJsonVersion())
    or
(pr, containerTag, mergedPrNo) = defraUtils.getVariables('ffc-demo-payment-service-core', defraUtils.getCSProjVersion())
```

### updateGithubCommitStatus

Updates the build status for the current commit in the github repository. The Jenkins server requires `repo:status` permissions for the repository.

The method takes two parameters:
- a message
- the state which can be `PENDING`, `SUCCESS`, or `FAILURE`

Example usage:

```
defraUtils.updateGithubCommitStatus('Build started', 'PENDING')
```

Note: the library initialises member variables `repoUrl` and `commitSha` when the `getVariables` method is run. These need to be set for the `updateGithubCommitStatus` method to work correctly.

There are 3 shortcut methods in the library for setting pending, failure and success. You should use these instead of calling this method directly.

### setGithubStatusPending / build.setGithubStatusPending

Updates the build status for the current commit to "Pending". See `updateGithubCommitStatus` for further information, as that method is called by this one.

The method takes a single optional parameter
- a message. This defaults to `Build started` if nothing is passed

New usage:

```
build.setGithubStatusPending()
```

### setGithubStatusSuccess / build.setGithubStatusSuccess

Updates the build status for the current commit to "Success". See `updateGithubCommitStatus` for further information, as that method is called by this one.

The method takes a single optional parameter
- a message. This defaults to `Build successful` if nothing is passed

New usage:

```
build.setGithubStatusSuccess()
```

### setGithubStatusFailure / build.setGithubStatusFailure

Updates the build status for the current commit to "Failed". See `updateGithubCommitStatus` for further information, as that method is called by this one.

The method takes a single parameter
- a message.

New usage:

```
build.setGithubStatusFailure(error.message)
```

### buildAndPushContainerImage / build.buildAndPushContainerImage

Builds the image from the docker-compose file and pushes it to a repository.

Takes four parameters:
- the ID of the docker registry credentials previously set up in Jenkins
- registry URL without the protocol
- the name of the image
- container image tag

New usage:

```
build.buildAndPushContainerImage('myRegCreds', 'myregistry.mydockerhub.com', 'ffc-demo-web', 'pr53')
```
### runTests / build.runTests

Uses the image built by the previous command to run tests.
By convention tests write results out to the folder `test-output`.
Junit tests are published to Jenkins from the file `test-output/junit.xml`, and the contents of `test-output` are removed after tests are published.

Takes three parameters:
- project name, e.g. `ffc-demo-web`
- service name to run from the project's docker-compose configuration, e.g. `app`
- build number

New usage, using the Jenkins global variable `BUILD_NUMBER` as the suffix:

```
build.runTests('ffc-demo-web', 'app', BUILD_NUMBER)
```

### buildTestImage / build.buildTestImage

Builds the test image using the docker-compose files in the repository. By convention the services are named the same as the image.

Takes four parameters:
- the ID of the docker registry credentials previously set up in Jenkins
- registry URL without the protocol
- project name, e.g. `ffc-demo-web`
- build number

New usage, using the Jenkins global variable `BUILD_NUMBER` as the suffix:

```
build.buildTestImage('myRegCreds', 'myregistry.mydockerhub.com', 'ffc-demo-web', BUILD_NUMBER)
```
