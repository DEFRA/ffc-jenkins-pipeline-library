# Jenkins pipeline library

## Overview

Common scripts for use in Jenkins pipelines used in build and deployment.

## Versioning

The library is versioning following the [semantic versioning specification](https://semver.org/). When updating the library you will need to increment the version number in the `VERSION` file. Upon merge to the master branch a new github release will automatically be created tagged with the `MAJOR`, `MINOR` and `PATCH` versions. For example version `2.4.6` will have the tags `2`, `2.4` and `2.4.6`.

## Usage

### New build procedure
A default node pipeline is now available and should be used in preference to the individual method calls in the `DefraUtils` class. This should very significantly reduce the amount of code required in your repository's Jenkinsfile:

```
@Library('defra-library@4') _

buildNodeJs environment: 'dev'
```

Specifying an environment means that in future we'll be able to support pipelines for different clusters, such as staging and production environments, although these clusters aren't yet in existance. 
If your pipeline has additional steps, pass a callback in the following manner:
```
buildNodeJs environment: 'dev'
```

Should you have a pipeline where you need to call the various methods individually, these have been moved to individual files:
| DefraUtils | New Equivalent |
|------------|:----------------:|
| destroyPrSqsQueues | queues.destroyPrSqsQueues |
| provisionPrSqsQueue | queues.provisionPrSqsQueue |
| provisionPrDatabaseRoleAndSchema | database.provisionPrDbRoleAndSchema |
| destroyPrDatabaseRoleAndSchema | database.destroyPrDbRoleAndSchema |
| getCSProjVersion | version.getCSProjVersion |
| getPackageJsonVersion | version.getPackageJsonVersion |
| verifyCSProjVersionIncremented | version.verifyCSProjIncremented |
| verifyPackageJsonVersionIncremented | version.verifyPackageJsonIncremented |
| getVariables | build.getVariables |
| setGithubStatusSuccess | build.setGithubStatusSuccess |
| setGithubStatusPending | build.setGithubStatusPtraending |
| setGithubStatusFailure | build.setGithubStatusFailure |
| lintHelm | test.lintHelm |
| buildTestImage | build.buildTestImage |
| runTests | build.runTests |
| createTestReportJUnit | test.createReportJUnit |
| deleteTestOutput | test.deleteOutput |
| analyseCode | test.analyseCode |
| waitForQualityGateResult | test.waitForQualityGateResult |
| buildAndPushContainerImage | build.buildAndPushContainerImage |
| deployChart | helm.deployChart |
| undeployChart | helm.undeployChart |
| publishChart | helm.publishChart |
| deployRemoteChart | helm.deployRemoteChart |
| triggerDeploy | deploy.trigger |
| triggerRelease | deploy.release |
| notifySlackBuildFailure | notifySlack.buildFailure |
| replaceInFile | utils.replaceInFile |
| getCommitMessage | utils.getCommitMessage |
The parameters taken by the function haven't changed, just the location.

### Following build procedure is deprecated and will be removed in v5.0.0
Register the library as a global pipeline library in the Jenkins Global Configuration.

Import the library with the `@library` annotation, including an optional tag or branch.
Semver tags are created for each release, so you can target the `MAJOR`, `MINOR` or `PATCH` of a release.
All the examples below are valid, assuming the global pipeline library has been imported as `defra-library`:

```
@Library('defra-library')
@Library('defra-library@master')
@Library('defra-library@3.1.2')
@Library('defra-library@3.1')
@Library('defra-library@3')
```

Import and instantiate the library:

```
import uk.gov.defra.ffc.DefraUtils
def defraUtils = new DefraUtils()
```
Note: that if the loading of the library is not immediately followed by an import, then the line must end with an underscore:
```
@Library('defra-library@0.0.1') _
```

Functions can be called on the instantiated library:

```
defraUtils.updateGithubCommitStatus('Build started', 'PENDING')
```

## Testing

A simple test harness may be run to unit test functions that are purely `groovy` code. This uses a groovy docker image and may be run via
```
./scripts/test
```

## Functions
Many of these are now obsolete, and will be removed in v5 of the pipeline. Where there is a direct equivalent with no difference in parameters, this is listed next to the function name. Where there is an equivalence, but with different parameters (e.g. `defraUtils.getVariables` / `build.getVariables`), the equivalent function is listed directly following the obsolete function with details on the new parameters. If there is no equivalent, these functions are not used by repo pipelines, but are dependencies of 'public' pipeline functions.

### tagCommit

Attaches a tag to a specified commit on a repo in the DEFRA github account. If the provided tag alreay exists on origin, it is deleted and reattached to the given commit SHA. If the tag does not exist on origin, it is created and pushed to origin.

It takes three parameters:
  * a string containing the tag to attach to the commit
  * a string containing the commit SHA
  * a string containing the name of the repository (assumed to be in the DEFRA github account)

### addSemverTags

Attaches a `MAJOR` and `MINOR` version tag to the lastest commit to a repo in the DEFRA github account. It uses `tagCommit` to perform the commit tagging on origin.

Examples:
  * provided with a `PATCH` increment to a verion (e.g. `2.3.5` to `2.3.6`), the `MAJOR` and `MINOR` tags (`2` and `2.3`) will be moved to the latest commit.
  * provided with a `MINOR` increment to a verion (e.g. `2.3.5` to `2.4.0`), the `MAJOR` tag (`2`) will be moved and the `MINOR` tag (`2.4`) will be created and attached to the latest commit.
  * provided with a `MAJOR` increment to a verion (e.g. `2.3.5` to `3.0.0`), the `MAJOR` and `MINOR` tags (`3` and `3.0`) will be created and attached to the latest commit.

It takes two parameters:
  * a string containing the current semver version for the release formatted as `MAJOR.MINOR.PATCH`
  * a string containing the name of the repository (assumed to be in the DEFRA github account)


### provisionPrSqsQueue / queues.provisionPrSqsQueue
This will provision sqs queues for a pull request, so any testing of the PR can be carried out in isolation.

Parameter details:
repoName - used to name the queue and the folder for tracking the queue in the git repo
prCode - the numeric code used for the PR, used to name the queue and the folder for tracking the queue in the git repo
queuePurpose - used to name the queue and the folder for tracking the queue in the git repo
serviceCode - three letter code for the workstream, e.g. 'FFC', used in the tags of the SQS queue, to identify the queue
serviceName - usually the same as the serviceCode, to be used in the tags of the SQS queue, to identify the queue
serviceType - a fuller description of the workstream, e.g. 'Future Farming Services', used in the tags of the SQS queue, to identify the queue

The queue is named in the format [repo name]-pr[pr code]-[queue purpose]. When combined, repo name, pr code and queue purpose shouldn't exceed 76 characters, this is a limit imposed by AWS.

### destroyPrSqsQueues / queues.destroyPrSqsQueues
This will tear down SQS queues provisioned by `provisionPrSqsQueue` and should be called when a PR is closed. It only requires the repo name and pr number and a single call to tear down all the queues that were created, which may have taken several calls to `provisionPrSqsQueue`
The repo name and pr code should be the same as the ones used for all queues created. If a different value was used for any queues (for whatever reason), that will necessitate a separate call to this function with the alternative values.

### provisionPrDatabaseRoleAndSchema / database.provisionPrDatabaseRoleAndSchema

Creates a Postgres database role and schema for use by a PR. See [associated confluence page](https://eaflood.atlassian.net/wiki/spaces/FPS/pages/1596653973/Creating+PR+database+namespaces+in+postgres) for context.

It takes six parameters:
- a string describing the Jenkins secret text credentials ID that contains database host to connect to
- a string describing the database name that the schema will be created in
- a string describing the Jenkins usernamePassword credentials ID that contains the database user and password
- a string describing the Jenkins usernamePassword credentials ID that contains the password for the role that will be created
- a string describing the PR number, e.g. `53`
- (optional, default=`false`) a boolean to determine if the SQL commands to create the PR role and schema include the `IF NOT EXISTS` logic. If `true` the SQL commands **will not** error if the schema and/or role already exist. If `false` (the default) the SQL commands **will** error if the schema and/or role already exist and the pipeline wil exit.

It returns a list containing:
- the PR schema created
- the PR role created

### destroyPrDatabaseRoleAndSchema / database.destroyPrDatabaseRoleAndSchema

Drops a Postgres database PR role and schema. See [associated confluence page](https://eaflood.atlassian.net/wiki/spaces/FPS/pages/1596653973/Creating+PR+database+namespaces+in+postgres) for context.

It takes four parameters:
- a string describing the Jenkins secret text credentials ID that contains database host to connect to
- a string describing the database name containing the schema
- a string describing the Jenkins usernamePassword credentials ID that contains the database user and password
- a string describing the PR number, e.g. `53`

### getCSProjVersion / version.getCSProjVersion

Returns the project version from the `[projectName].csproj` file. It requires the project name to be passed as a parameter, but this means that in a solution of several projects, versions can be retrieved for each of them.

### getCSProjVersionMaster

Returns the project version from the `[projectName].csproj` file in the master branch. It requires the project name to be passed as a parameter, but this means that in a solution of several projects, versions can be retrieved for each of them.

### verifyCSProjVersionIncremented / version.verifyCSProjVersionIncremented
Compares the master version with the branch version from the provided project name.
If the version has been incremented correctly a message will be `echoed` displaying the new and the old version, i.e.

`version increment valid '1.0.0' -> '1.0.1'`.

If the version has not incremented correctly, or is invalid, an error will be thrown containing the new and the old versions, i.e.

`version increment invalid '1.0.0' -> '1.0.0'`.

The function requires the project name to be passed as a parameter.

### getPackageJsonVersion / version.getPackageJsonVersion

Returns the package version from the `package.json` file.

### getPackageJsonVersionMaster

Returns the package version from the `package.json` file in the master branch.

### verifyPackageJsonVersionIncremented / version.verifyPackageJsonIncremented
Compares the master version with the branch version of the `package.json`.
If the version has been incremented correctly message will be `echoed` displaying the new and the old version, i.e.

`version increment valid '1.0.0' -> '1.0.1'`.

If the version has not incremented correctly, or is invalid, an error will be thrown containing the new and the old versions, i.e.

`version increment invalid '1.0.0' -> '1.0.0'`.

### getFileVersion

Returns the contents of a given file. The assumption is that this file contains a single string that is a semver formatted version number: `MAJOR.MINOR.PATCH`.

It takes one parameter:
  * a string containing the file name of the file containing the version number

### getFileVersionMaster

Returns the contents of a given file in the master branch. The assumption is that this file contains a single string that is a semver formatted version number: `MAJOR.MINOR.PATCH`.

It takes one parameter:
  * a string containing the file name of the file containing the version number

### verifyFileVersionIncremented

Compares the master version with the branch version defined in a given file.
If the version has been incremented correctly message will be `echoed` displaying the new and the old version, i.e.

`version increment valid '1.0.0' -> '1.0.1'`.

If the version has not incremented correctly, or is invalid, an error will be thrown containing the new and the old versions, i.e.

`version increment invalid '1.0.0' -> '1.0.0'`.

It takes one parameter:
  * a string containing the file name of the file containing the version number

### versionHasIncremented

Function used internally in the `verifyCSProjVersionIncremented`, `verifyPackageJsonVersionIncremented` and `verifyFileVersionIncremented` functions.

Takes two parameters of the versions to compare, typically master version and branch version.

The function returns `true` if both versions are valid semvers, and the second version is higher than the first.

The function returns `false` if either version is invalid, or the second version is not higher than the first.

### errorOnNoVersionIncrement

Convenience method shared by `verifyPackageJsonVersionIncremented` and `verifyCSProjVersionIncremented` to throw error when the version has not been incremented, or is invalid.

Takes two parameters - master version and branch version.

### replaceInFile / utils.replaceInFile

Utility method to globally substitute text in a file.

The function takes three parameters:
- `from`: the text to match
- `to`: the new text to replace the matched text
- `file`: path to file

The `from` and `to` values are used directly in a sed command, so wildcards may be used.

It can be tricky to correctly escape characters for sed. For example the forward slash `/`
needs to be escaped with a back slash, which itself need to be escaped with a back slash so the valid encoding for `/` is  `\\/`.

Forward slashes in the final `file` parameter do not need escaping.

Example usage to replace the path `/usr/src/app` with `./app` a path in the file `./lcov.info`:

```
replaceInFile('\\/usr\\/src\\/app', '.\\/app', './lcov.info')
```
OR
```
utils.replaceInFile('\\/usr\\/src\\/app', '.\\/app', './lcov.info')
```

### getMergedPrNo

Parses the local commit log to obtain the merged PR number from the message. This is reliant on the standard github merge message of the PR name followed by the PR number, i.e.
```
Update license details (#53)
```

The function returns the PR number for a merge of the appropriate format, i.e. `pr53` or an empty string if not.

### getRepoUrl

Obtains the remote URL of the current repository, i.e. `https://github.com/DEFRA/ffc-demo-web.git`

### getCommitSha

Return the SHA hash of the latest commit for the current repository, i.e. `9fd0a77d3eaa3d4370d3f31158f37dd8abd19fae`

### getCommitMessage

Return the message of the latest commit for the current repository.

### verifyCommitBuildable

If the build is a branch with a Pull Request (PR), or the master branch a message will be `echoed` describing the type of build.

If the build is not for a PR or the master branch an error will be thrown with the message `Build aborted - not a PR or a master branch`

### getVariables (Obsolete, see build.getVariables, below)

Takes the repository name as a parameter, i.e. `ffc-demo-web`, as well as the relevant version parameter to call depending on the project type.

It returns information required by the build steps as an array
- the PR number, i.e. `53`
- the container tag, either the semver number (for master branch) or the PR number prefixed with pr, i.e. `pr53`
- the merged PR number

Example usage:

```
(pr, containerTag, mergedPrNo) = defraUtils.getVariables('ffc-demo-payment-service', defraUtils.getPackageJsonVersion())
    or
(pr, containerTag, mergedPrNo) = defraUtils.getVariables('ffc-demo-payment-service-core', defraUtils.getCSProjVersion())
```

### build.getVariables

Unlike the DefraUtils version, it no longer takes the repository name as a parameter, this is determined automatically. The only parameter is the relevant version parameter to call depending on the project type.

It returns information required by the build steps as an array
- the repo name, e.g. `ffc-demo-web`
- the PR number, i.e. `53`
- the container tag, either the semver number (for master branch) or the PR number prefixed with pr, i.e. `pr53`
- the merged PR number

Example usage:

```
(repoName, pr, containerTag, mergedPrNo) = build.getVariables(defraUtils.getPackageJsonVersion())
    or
(repoName, pr, containerTag, mergedPrNo) = build.getVariables(defraUtils.getCSProjVersion())
```

### updateGithubCommitStatus

Updates the build status for the current commit in the github repository. The Jenkins server requires `repo:status` permissions for the repository.

The function takes two parameters:
- a message
- the state which can be `PENDING`, `SUCCESS`, or `FAILURE`

Example usage:

```
defraUtils.updateGithubCommitStatus('Build started', 'PENDING')
```

Note: the library initialises member variables `repoUrl` and `commitSha` when the `getVariables` function is run. These need to be set for the `updateGithubCommitStatus` function to work correctly.

There are 3 shortcut methods in the library for setting pending, failure and success. You should use these instead of calling this method directly.

### setGithubStatusPending / build.setGithubStatusPending

Updates the build status for the current commit to "Pending". See `updateGithubCommitStatus` for further information, as that method is called by this one.

The function takes a single optional parameter
- a message. This defaults to `Build started` if nothing is passed

New usage:

```
build.setGithubStatusPending()
```

### setGithubStatusSuccess / build.setGithubStatusSuccess

Updates the build status for the current commit to "Success". See `updateGithubCommitStatus` for further information, as that method is called by this one.

The function takes a single optional parameter
- a message. This defaults to `Build successful` if nothing is passed

New usage:

```
build.setGithubStatusSuccess()
```

### setGithubStatusFailure / build.setGithubStatusFailure

Updates the build status for the current commit to "Failed". See `updateGithubCommitStatus` for further information, as that method is called by this one.

The function takes a single parameter
- a message.

New usage:

```
build.setGithubStatusFailure(error.message)
```

### lintHelm / test.lintHelm
Lints Helm chart within repository.

By convention Helm charts are stored in the folder `helm` in a subfolder the same name as the image, service, and repository.

Takes one parameter:
- chart name

New usage:

```
test.lintHelm('ffc-demo-web')
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

### createTestReportJUnit / test.createReportJUnit

Creates a test report using JUnit

### deleteTestOutput / test.deleteOutput

Deletes the test-output folder from the workspace, acting as a user in a container using a given image.

Takes two parameters:
- name of the container image to use for the delete command
- path to the default working directory of the container image

New usage:

```
test.deleteTestOutput('ffc-demo-web', '/home/node')
```

### analyseCode / test.analyseCode

Triggers static code analysis using SonarQube.

Dependent on integration between Jenkins and SonarQube being configured.

This step should run after any test tasks so the test coverage output can be assessed.

Takes three parameters:
- name of SonarQube environment configured in Jenkins plugin
- name of SonarScanner configured in Jenkins plugin
- additional parameters to be added to SonarScanner command.  Of which `sonar.projectKey` and `sonar.Sources` are mandatory

New usage:

```
test.analyseCode(sonarQubeEnv, sonarScanner, ['sonar.projectKey' : 'ffc-demo-web', 'sonar.sources' : '.'])
```

### waitForQualityGateResult / test.waitForQualityGateResult

Waits for static code analysis result via SonarQube webhook.

Dependent on integration between Jenkins and SonarQube being configured.

This step should run after `analyseCode` as it is dependent on the SonarQube run ID generated from that task to know which result to wait for.

Takes one parameters:
- timeout in minutes pipeline should wait for webhook response before aborting.

New usage:

```
test.waitForQualityGateResult(5)
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

### deployChart (Obsolete, see helm.deployChart below)

Deploys the Helm chart stored in the repository to Kubernetes.
By convention Helm charts are stored in the folder `helm` in a subfolder the same name as the image, service, and repository.

Development Helm charts are deployed with the name and namespace set to a combination of the image name and tag, i.e. `ffc-demo-web-pr53`

Takes five parameters:
- The ID of the Kubernetes credentials previously setup in Jenkins
- the registry where the chart's image is stored
- the name of the chart (note this must also be the name of the image to deploy)
- container image tag to deploy
- additional command line parameters to send to the Helm deployment

Example usage:

```
def extraCommands = "--values ./helm/ffc-demo-web/dev-values.yaml"
defraUtils.deployChart('kubeconfig01', 'myregistry.mydockerhub.com', 'ffc-demo-web', 'pr53', extraCommands)
```

### helm.deployChart

Deploys the Helm chart stored in the repository to Kubernetes.
By convention Helm charts are stored in the folder `helm` in a subfolder the same name as the image, service, and repository.

Development Helm charts are deployed with the name and namespace set to a combination of the image name and tag, i.e. `ffc-demo-web-pr53`

Takes four parameters:
- environment to deploy to (this is used to determine which K8s credentials to use)
- the registry where the chart's image is stored
- the name of the chart (note this must also be the name of the image to deploy)
- container image tag to deploy

The 'extraCommands' parameter previously specified inline have now been moved to Jenkins secret values, `pr-values.yaml` and `dev-values.yaml`

New usage:

```
helm.deployChart('dev', 'myregistry.mydockerhub.com', 'ffc-demo-web', 'pr53')
```

### undeployChart (Obsolete, see helm.undeployChart below)

Removes a Helm chart previously deployed to a Kubernetes cluster.
Both the chart and the namespace are removed when the chart is undeployed.

Takes three parameters:
- The ID of the Kubernetes credentials previously setup in Jenkins
- the name of the chart
- container image tag that was deployed by this chart

New usage:

```
defraUtils.undeployChart('kubeconfig01', 'ffc-demo-web', 'pr53')
```

### helm.undeployChart

Removes a Helm chart previously deployed to a Kubernetes cluster.
Both the chart and the namespace are removed when the chart is undeployed.

Takes three parameters:
- environment to undeploy from
- the name of the chart
- container image tag that was deployed by this chart

New usage:

```
helm.undeployChart('dev', 'ffc-demo-web', 'pr53')
```

### publishChart / helm.publishChart

Publishes the local Helm chart to a Helm chart repository implemented in git.

Takes three parameters:
- docker registry without the protocol
- chart name
- container image tag

New usage:

```
helm.publishChart('myregistry.mydockerhub.com', 'ffc-demo-web', 'master')
```

### deployRemoteChart (Obsolete, see helm.deployRemoteChart below)

Deploys a Helm chart from a remote chart repository to Kubernetes.

Takes four parameters:
- the Kubernetes namespace to deploy into
- the chart name
- the chart version
- additional command line parameters to send to the Helm deployment

New usage:

```
def extraCommands = "--values ./helm/ffc-demo-web/aws-values.yaml"
defraUtils.deployRemoteChart('ffc-demo', 'ffc-demo-web', '1.1.7', extraCommands)
```

### helm.deployRemoteChart

Deploys a Helm chart from a remote chart repository to Kubernetes.

Takes four parameters:
- the environment to deploy into
- the Kubernetes namespace to deploy into
- the chart name
- the chart version

The 'extraCommands' parameter previously specified inline have now been moved to Jenkins secret values, `pr-values.yaml` and `dev-values.yaml`

New usage:

```
helm.deployRemoteChart('dev', 'ffc-demo', 'ffc-demo-web', '1.1.7')
```

### triggerDeploy / deploy.trigger

Triggers the Jenkins deployment job to deploy the built image.

Takes four parameters:
- the full url (without trailing /) of the jenkins service, including username and access token
  e.g. `https://deploy:accesstoken@jenkins.ffc.aws-int.defra.cloud`
- the jenkins job name for the deploy job, this is in the url when you edit or view the job in Jenkins
- the token that is set up when you configured the job in Jenkins. You must tick the "Trigger builds remotely" option when configuring the job. The Authentication token entered into the job is the one that should be passed here.
- an object, that should contain all the parameters that need to be passed to the job (if required), for example `['version': '1.0.0']`


New usage:

```
deploy.trigger((jenkinsDeployUrl, deployJobName, jenkinsToken, ['chartVersion':'1.0.0'])
```


### releaseExists

Checks GitHub to determine if a given Release Tag already exists for that repo.

Takes three parameters:
- the container tag in semver format to check for on GitHub e.g 1.0.0
- the repository name to check
- the GitHub connection token secret text

```
releaseExists(containerTag, repoName, token)
```

### triggerRelease / release.trigger

Triggers a release to be created on GitHub for a given repo only where a release with the identical semver does not already exist

Takes four parameters:
- the container tag in semver format to check for on GitHub e.g 1.0.0
- the repository name to check
- the release description text
- the GitHub connection token secret text

```
release.trigger(containerTag, repoName, releaseDescription, token)
```


### notifySlackBuildFailure / notifySlack.buildFailure

Sends a message to the ffc-notifications Slack workspace when the jenkins build fails.

Takes two parameters:
- the failure reason to display in the slack notification e.g. `e.message`
- optional channel name for none master branch build failures to be reported in, must contain the # before the channel name e.g. `#generalbuildfailures`

Example usage:

```
notifySlack.buildFailure(e.message, "#generalbuildfailures")
```
