# Jenkins pipeline library

## Overview

Common scripts for use in Jenkins pipelines used in build and deployment.

## Usage

Register the library as a global pipeline library in the Jenkins Global Configuration.

Import the library with the `@library` annotation, including an optional tag or branch.
All the examples below are valid, assuming the global pipeline library has been imported as `defra-library`:

```
@Library('defra-library')
@Library('defra-library@master')
@Library('defra-library@0.0.1')
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
## Functions

### replaceInFile

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

`replaceInFile('\\/usr\\/src\\/app', '.\\/app', './lcov.info')`

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

### verifyCommitBuildable

If the build is a branch with a Pull Request (PR), or the master branch a message will be `echoed` describing the type of build.

If the build is not for a PR or the master branch an error will be thrown with the message `Build aborted - not a PR or a master branch`

### getVariables

Takes the repository name as a parameter, i.e. `ffc-demo-web`, and returns information required by the build steps as an array
- the PR number, i.e. `53`
- the container tag, either the branch name or the PR number prefixed with pr, i.e. `pr53`
- the merged PR number

Example usage:

```
(pr, containerTag, mergedPrNo) = defraUtils.getVariables('ffc-demo-web')
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

### setGithubStatusPending

Updates the build status for the current commit to "Pending". See `updateGithubCommitStatus` for further information, as that method is called by this one.

The function takes a single optional parameter
- a message. This defaults to `Build started` if nothing is passed

Example usage:

```
defraUtils.setGithubStatusPending()
```

### setGithubStatusSuccess

Updates the build status for the current commit to "Success". See `updateGithubCommitStatus` for further information, as that method is called by this one.

The function takes a single optional parameter
- a message. This defaults to `Build successful` if nothing is passed

Example usage:

```
defraUtils.setGithubStatusSuccess()
```

### setGithubStatusFailure

Updates the build status for the current commit to "Failed". See `updateGithubCommitStatus` for further information, as that method is called by this one.

The function takes a single parameter
- a message.

Example usage:

```
defraUtils.setGithubStatusFailure(error.message)
```

### lintHelm
Lints Helm chart within repository.  

By convention Helm charts are stored in the folder `helm` in a subfolder the same name as the image, service, and repository.

Takes one parameter:
- container image name

Example usage:

```
defraUtils.lintHelm('ffc-demo-web')
```

### buildTestImage
Builds the test image using the docker-compose files in the repository. By convention the services are named the same as the image.

Takes two parameters:
- container image name
- a suffix used to run the docker-compose as a unique project name

Example usage, using the Jenkins global variable `BUILD_NUMBER` as the suffix:

```
defraUtils.buildTestImage('ffc-demo-web', BUILD_NUMBER)
```

### runTests

Uses the image built by the previous command to run tests.
By convention tests write results out to the folder `test-output`.
Junit tests are published to Jenkins from the file `test-output/junit.xml`, and the contents of `test-output` are removed after tests are published.

Takes two parameters:
- container image name
- a suffix used to run the docker-compose as a unique project name

Example usage, using the Jenkins global variable `BUILD_NUMBER` as the suffix:

```
defraUtils.runTests('ffc-demo-web', BUILD_NUMBER)
```

### analyseCode

Triggers static code analysis using SonarQube.  

Dependent on integration between Jenkins and SonarQube being configured.

This step should run after any test tasks so the test coverage output can be assessed.

Takes three parameters:
- name of SonarQube environment configured in Jenkins plugin
- name of SonarScanner configured in Jenkins plugin
- additional parameters to be added to SonarScanner command.  Of which `sonar.projectKey` and `sonar.Sources` are mandatory

Example usage:

```
defraUtils.analyseCode(sonarQubeEnv, sonarScanner, ['sonar.projectKey' : 'ffc-demo-web', 'sonar.sources' : '.'])
```

### waitForCodeAnalysisResult

Waits for static code analysis result via SonarQube webhook.  

Dependent on integration between Jenkins and SonarQube being configured.

This step should run after `analyseCode` as it is dependent on the SonarQube run ID generated from that task to know which result to wait for.  

Takes one parameters:
- timeout in minutes pipeline should wait for webhook response before aborting.

Example usage:

```
defraUtils.waitForQualityGateResult(5)
```

### buildAndPushContainerImage

Builds the image from the docker-compose file and pushes it to a repository.

Takes four parameters:
- the ID of the docker registry credentials previously set up in Jenkins
- registry URL without the protocol
- the name of the image
- the image tag

Example usage:

```
defraUtils.buildAndPushContainerImage('myRegCreds', 'myregistry.mydockerhub.com', 'ffc-demo-web', 'pr53')
```

### deployChart

Deploys the Helm chart stored in the repository to Kubernetes.
By convention Helm charts are stored in the folder `helm` in a subfolder the same name as the image, service, and repository.

Development Helm charts are deployed with the name and namespace set to a combination of the image name and tag, i.e. `ffc-demo-web-pr53`

Takes five parameters:
- The ID of the Kubernetes credentials previously setup in Jenkins
- the registry where the chart's image is stored
- the name of the image
- the name of the tag
- additional command line parameters to send to the Helm deployment

Example usage:

```
def extraCommands = "--values ./helm/ffc-demo-web/dev-values.yaml"
defraUtils.deployChart('kubeconfig01', 'myregistry.mydockerhub.com', 'ffc-demo-web', 'pr53', extraCommands)
```

### undeployChart

Removes a Helm chart previously deployed to a Kubernetes cluster.
Both the chart and the namespace are removed when the chart is undeployed.

Takes three parameters:
- The ID of the Kubernetes credentials previously setup in Jenkins
- the name of the image
- the name of the tag


Example usage:

```
defraUtils.undeployChart('kubeconfig01', 'ffc-demo-web', 'pr53')
```

### publishChart

Publishes the local Helm chart to a Helm chart repository implemented in git.

Takes three parameters:
- docker registry without the protocol
- container image name
- container tag

Example usage:

```
 defraUtils.publishChart('myregistry.mydockerhub.com', 'ffc-demo-web', 'master')
```

### triggerDeploy

Triggers the Jenkins deployment job to deploy the built image.

Takes four parameters:
- the full url (without trailing /) of the jenkins service, including username and access token
  e.g. `https://deploy:accesstoken@jenkins.ffc.aws-int.defra.cloud`
- the jenkins job name for the deploy job, this is in the url when you edit or view the job in Jenkins
- the token that is set up when you configured the job in Jenkins. You must tick the "Trigger builds remotely" option when configuring the job. The Authentication token entered into the job is the one that should be passed here.
- an object, that should contain all the parameters that need to be passed to the job (if required), for example `['version': '1.0.0']`


Example usage:

```
defraUtils.triggerDeploy((jenkinsDeployUrl, deployJobName, jenkinsToken, ['chartVersion':'1.0.0'])
```
