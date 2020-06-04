# build

> Build related methods

Below are the methods available on the script. They can be executed by calling
`<script>.<method>` e.g. `build.getVariables()`

## getVariables

Supplied with the version of the repo being built, an array containing the
following will be returned:
- the repo name, e.g. `ffc-demo-web`
- the PR number, i.e. `53`
- the tag that will be used, either the SemVer number (for master
  branch) or the PR number prefixed with pr, i.e. `pr53`
- the merged PR number

Example usage:

```
(repoName, pr, tag, mergedPrNo) = build.getVariables(version.getPackageJsonVersion())
    or
(repoName, pr, tag, mergedPrNo) = build.getVariables(version.getCSProjVersion())
```

## updateGithubCommitStatus

Updates the build status for the current commit in the GitHub repository. The
Jenkins server requires `repo:status` permissions for the repository.

The method takes two parameters:
- a message
- the state which can be `PENDING`, `SUCCESS`, or `FAILURE`

Example usage:

```
build.updateGithubCommitStatus('Build started', 'PENDING')
```

Note: the method requires member variables `repoUrl` and `commitSha` to be set
prior to running `getVariables`. Without these variables being set
`updateGithubCommitStatus` method will fail to work correctly.

There are 3 shortcut methods in the library for setting pending, failure and
success. You should use these instead of calling this method directly.

## setGithubStatusPending

Updates the build status for the current commit to "Pending". See
`updateGithubCommitStatus` for further information, as that method is called by
this one.

The method takes a single optional parameter
- a message. This defaults to `Build started` if nothing is passed

Example usage:

```
build.setGithubStatusPending()
```

## setGithubStatusSuccess

Updates the build status for the current commit to "Success". See
`updateGithubCommitStatus` for further information, as that method is called by
this one.

The method takes a single optional parameter
- a message. This defaults to `Build successful` if nothing is passed

Example usage:

```
build.setGithubStatusSuccess()
```

## setGithubStatusFailure

Updates the build status for the current commit to "Failed". See
`updateGithubCommitStatus` for further information, as that method is called by
this one.

The method takes a single parameter
- a message.

Example usage:

```
build.setGithubStatusFailure(error.message)
```

## buildAndPushContainerImage

Builds the image from the docker-compose file and pushes it to a repository.

Takes four parameters:
- the ID of the docker registry credentials previously set up in Jenkins
- registry URL without the protocol
- the name of the image
- container image tag

Example usage:

```
build.buildAndPushContainerImage('myRegCreds', 'myregistry.mydockerhub.com', 'ffc-demo-web', 'pr53')
```

## runTests

Uses the image built by the previous command to run tests.
By convention tests write results out to the folder `test-output`.
JUnit tests are published to Jenkins from the file `test-output/junit.xml`, and
the contents of `test-output` are removed after tests are published.

Takes three parameters:
- project name, e.g. `ffc-demo-web`
- service name to run from the project's docker-compose configuration e.g. `app`
- build number

Example usage, using the Jenkins global variable `BUILD_NUMBER` as the suffix:

```
build.runTests('ffc-demo-web', 'app', BUILD_NUMBER)
```

## buildTestImage

Builds the test image using the docker-compose files in the repository. By
convention the services are named the same as the image.

Takes four parameters:
- the ID of the docker registry credentials previously set up in Jenkins
- registry URL without the protocol
- project name, e.g. `ffc-demo-web`
- build number

Example usage, using the Jenkins global variable `BUILD_NUMBER` as the suffix:

```
build.buildTestImage('myRegCreds', 'myregistry.mydockerhub.com', 'ffc-demo-web', BUILD_NUMBER)
```

## npmAudit

Runs [npm audit](https://docs.npmjs.com/cli/audit) against the project.

Takes three parameters:
- `auditLevel` - level of vulnerabilities at which the audit will fail.
  Default is `moderate`. Suitable options are detailed in the
  [audit docs](https://docs.npmjs.com/cli/audit.html#synopsis)
- `logType` - type of log to output. Default is `parseable` due to its succinct
  nature. Other options include `json` and if the default option of the very
  long log is required any truthy value can be used e.g. `long`.
- `failOnIssues` - flag to determine if the step should fail if issues are
  found in the audit. Default is `false`

Example usage:

```
build.npmAudit('critical', null, true)
```
