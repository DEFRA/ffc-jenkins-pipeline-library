# build

> Build related methods

Below are the methods available on the script. They can be executed by calling
`<script>.<method>` e.g. `build.getVariables()`

## getDefaultBranch

Returns the default branch for a repository, if a value is supplied then it will be used,
otherwise the default `main` will be used.

Takes two parameters
- the default branch to use if one is not supplied, e.g. `main`
- optional override to the default

## checkoutSourceCode

Checks out the source code for the current repository branch.
As the repository version is validated against the `main` branch, the remote `main` branch
will also be fetched to ensure it is available for comparison.

Takes one parameter
- the default branch name, e.g. `main`

## getVariables

Supplied with the version of the repo being built, an array containing the
following will be returned:
- the repo name, e.g. `ffc-demo-web`
- the PR number, i.e. `53`
- the tag that will be used, either the SemVer number (for main
  branch) or the PR number prefixed with pr, i.e. `pr53`
- the merged PR number

Example usage:

```
(repoName, pr, tag, mergedPrNo) = build.getVariables(version.getPackageJsonVersion())
    or
(repoName, pr, tag, mergedPrNo) = build.getVariables(version.getCSProjVersion())
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

Takes six parameters:
- project name, e.g. `ffc-demo-web`
- service name to run from the project's docker-compose configuration e.g.
  `app`
- build number
- tag
- pr
- environment

Example usage, using the Jenkins global variable `BUILD_NUMBER` as the suffix:

```
build.runTests('ffc-demo-web', 'app', BUILD_NUMBER, tag, pr, environment)
```

## buildTestImage

Builds the test image using the docker-compose files in the repository. By
convention the services are named the same as the image.

Takes five parameters:
- the ID of the docker registry credentials previously set up in Jenkins
- registry URL without the protocol
- project name, e.g. `ffc-demo-web`
- build number
- tag

Example usage, using the Jenkins global variable `BUILD_NUMBER` as the suffix:

```
build.buildTestImage('myRegCreds', 'myregistry.mydockerhub.com', 'ffc-demo-web', BUILD_NUMBER, tag)
```

## npmAudit

Runs [npm audit](https://docs.npmjs.com/cli/audit) against the project. If any
issues are identified with a
[severity](https://docs.npmjs.com/about-audit-reports#severity) of `moderate`
or above the build will be failed.

Issues can be resolved by running
[`npm audit fix`](https://docs.npmjs.com/cli/audit).

Takes six parameters:
- `auditLevel` - level of vulnerabilities at which the audit will fail.
  Default is `moderate`. Suitable options are detailed in the
  [audit docs](https://docs.npmjs.com/cli/audit.html#synopsis)
- `logType` - type of log to output. Default is `parseable` due to its succinct
  nature. Other options include `json` and if the default option of the very
  long log is required any truthy value can be used e.g. `long`.
- `failOnIssues` - flag to determine if the step should fail if issues are
  found in the audit. Default is `true`
- `containerImage` - name of the container image to use for the npm audit
- `containerWorkDir` - path to the default working directory of the container
  image
- `pr` - the pr number of the build or an empty string if the build is for the
  main branch

Example usage:

```
build.npmAudit('critical', null, false)
```

## Extract snyk files

A utility method to run a docker-compose file to move files required by Snyk from a built container
image to the local file system.


Takes three parameters:
- project name, e.g. `ffc-demo-payment-service-core`
- build number
- tag

This is currently used in the [buildDotNetCore](buildDotNetCore.groovy) script to copy assets from the container
so Snyk may analyse the project.

The method expects a file named `docker-compose.snyk.yaml` to be present in the project to extract any required
files to the correct location.

As well as extracting the required files the `docker-compose.snyk.yaml` should ensure the files writable by the Jenkins user to enable cleanup.

Depending on the files extracted, and the location they are written too, the target folders may need
their permissions changing.

Example usage:

```
sh("chmod 777 ${config.project}/obj || mkdir -p -m 777 ${config.project}/obj")
build.extractSynkFiles(repoName, BUILD_NUMBER, tag)
```

## snykTest

Runs
[snyk test](https://support.snyk.io/hc/en-us/articles/360003812578-CLI-reference)
against the project. If any issues are identified with a
[severity](https://support.snyk.io/hc/en-us/articles/360001040078-How-is-a-vulnerability-s-severity-determined-)
of `medium` or above, the build will be failed when running for PR builds only.
When the step runs on a build of the main branch, only warnings will be
produced so main branch builds are not blocked, should vulnerabilities have
been identified since the last changes were made to the main branch.

Details of how to resolve issues are covered within the (internal)
[Snyk](https://eaflood.atlassian.net/wiki/spaces/FPS/pages/1943897107/Snyk#Resolving-issues)
confluence page. If the page is not accessible, see the documentation on
[snyk.io](https://support.snyk.io/hc/en-us/articles/360003891038-Fix-your-vulnerabilities).

Takes five parameters, with `targetFile` being optional:
- `failOnIssues` - should the job fail when issues are detected. Default is
  `true`
- `organisation` - against which the report should be recorded. Default is the
  env var `SNYK_ORG`
- `severity` - of issues to be reported. Default is `medium`
- `targetFile` - [optional] name of file to analyse
- `pr` - the pr number of the build or an empty string if the build is for the
  main branch

```
build.snykTest(true, 'my-org-name', 'low')
build.snykTest(true, 'my-org-name', 'low', 'my-project.sln')
```

## getImageName
Returns a container image name based on the repository and registry to publish to.

Takes four parameters:
- `repoName` - name of the repository
- `tag` - version of the image to add as a tag.  Pass 'latest' here without a `tagSuffix` for a latest tag.
- `tagSuffix` - optional parameter to be appended to to the version tag, will automatically be prefixed with `-`
- `registry` - optional parameter to specify a container registry other than `defradigital`

```
String imageName = build.getImageName('ffc-dotnet-core-sonar', '1.0.0', 'dotnet3.1', 'myregistry')
```

## buildContainerImage
Builds a container image from a Dockerfile

Takes one parameter:
- `imageName` - name of image to tag the build with

```
build.buildContainerImage('defradigital/ffc-dotnet-core-sonar:1.0.0-dotnet3.1')
```

## containerTagExists
Checks to see if an image already exists in a repository

Takes one parameter:
- `imageName` - full image name

```
Boolean tagExists = build.containerTagExists('defradigital/ffc-dotnet-core-sonar:1.0.0-dotnet3.1')
```
