### lintHelm / test.lintHelm

Lints Helm chart within repository.

By convention Helm charts are stored in the folder `helm` in a subfolder the same name as the image, service, and repository.

Takes one parameter:
- chart name

New usage:

```
test.lintHelm('ffc-demo-web')
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
