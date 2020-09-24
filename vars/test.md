# test

> Test related methods

Below are the methods available on the script. They can be executed by calling
`<script>.<method>` e.g. `test.lintHelm()`

## lintHelm

Lints Helm chart within repository.

By convention Helm charts are stored in the folder `helm` in a subfolder the
same name as the image, service and repository.

Takes one parameter:
- chart name

Example usage:

```
test.lintHelm('ffc-demo-web')
```

## runGitHubSuperLinter

Executes `./scripts/run-github-super-linter` which _should_ be a repository
specific `docker run` of
[github/super-linter](https://github.com/github/super-linter). See this repo's
[`run-github-super-linter`](../scripts/run-github-super-linter) version for an
example.
There are several reasons why the script is executed in this way as opposed
to having the shell command embedded within the Groovy code:
* There are a lot of configuration options for running Super-Linter and each
  repo which may want to run it is likely going to have a unique context.
  Therefore, rather than trying to encapsulate the complexity within the task,
  the configuration options should be set within the script and each repository
  can set those independently
* The same script can be run locally, allowing feedback prior to check-in
* As the repository changes and adheres to or differs from the linting rules,
  those changes will be recorded within the repository
* Few library changes will be required resulting in less maintenance

Takes one parameter:
- a boolean to determine if errors should be disabled. By default errors are
  enabled and will return a non-zero exit code, however, if it is desirable for
  errors to return an exit code of 0 the argument should be set to `true`

Example usage:

```
test.runGitHubSuperLinter(true)
```

## createJUnitReport

Creates a test report using JUnit.

## deleteOutput

Deletes the test-output folder from the workspace, acting as a user in a
container using a given image.

Takes two parameters:
- name of the container image to use for the delete command
- path to the default working directory of the container image

Example usage:

```
test.deleteOutput('ffc-demo-web', '/home/node')
```

## analyseCode

Triggers static code analysis using SonarQube.

Dependent on integration between Jenkins and SonarQube being configured.

This step should run after any test tasks so the test coverage output can be assessed.

Takes three parameters:
- name of SonarQube environment configured in Jenkins plugin
- name of SonarScanner configured in Jenkins plugin
- additional parameters to be added to SonarScanner command. Of which
  `sonar.projectKey` and `sonar.Sources` are mandatory

Example usage:

```
test.analyseCode(sonarQubeEnv, sonarScanner, ['sonar.projectKey' : 'ffc-demo-web', 'sonar.sources' : '.'])
```

## waitForQualityGateResult

Waits for static code analysis result via SonarQube webhook.

Dependent on integration between Jenkins and SonarQube being configured.

This step should run after `analyseCode` as it is dependent on the SonarQube
run ID generated from that task to know which result to wait for.

Takes one parameter:
- timeout in minutes pipeline should wait for webhook response before aborting.

Example usage:

```
test.waitForQualityGateResult(5)
```

## runAcceptanceTests

Executes the acceptance tests if an acceptance folder is found within the repo.
Report output is written to the workspace under
test/acceptance/html-reports/acceptance-test-suite-report.html

Takes one parameter:
- PR number (used for determining which deployment URL to execute the tests against)

Example usage:

```
test.runAcceptanceTests(74)
```

## runZapScan

Checks for the existence of `docker-compose.zap.yaml` file in the root of the
repository. If the file exists, the `zap-baseline-scan` service will be run. The
command _could_ be anything but it is likely to be running a
[Baseline Scan](https://www.zaproxy.org/docs/docker/baseline-scan/) against the
target of the local web server.
There are a number of options available to configure the scan appropriately for
the service under test, the linked page includes details.

Takes three parameters:
- project name, e.g. `ffc-demo-web`
- build number
- container image tag

Example usage:

```
test.runZapScan('ffc-demo-web', BUILD_NUMBER, 'pr99')
```
