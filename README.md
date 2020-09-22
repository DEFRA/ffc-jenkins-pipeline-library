# Jenkins pipeline library

[![GitHub Super-Linter](https://github.com/defra/ffc-jenkins-pipeline-library/workflows/Lint%20Code%20Base/badge.svg)](https://github.com/marketplace/actions/super-linter)

## Overview

Common scripts for use in Jenkins pipelines used in build and deployment.

## Versioning

The library is versioned following the principle of the
[semantic versioning specification](https://semver.org/). When updating the
library you will need to increment the version number in the
[VERSION](VERSION) file.
Upon merge to the master branch a new GitHub release will automatically be
created, tagged with the `MAJOR`, `MINOR` and `PATCH` versions. For example
version `2.4.6` will have the tags `2`, `2.4` and `2.4.6`.

**Note:** Due to the way versions of Jenkins shared libraries are resolved, a
single numeric tag such as `6` is not precise enough to prevent other branches
or SHAs from being matched and that version being used rather than the expected
tagged version of the library. To prevent this from causing problems the
SemVer version is prepended with a `v-`. Doing so means the tag will not be
mistakenly resolved against a SHA (alphanumeric only) although it might still
be matched against a branch. However, this is much less likely and is under the
control of the developers.
The unique tag doesn't need to be `v-` and is easily changed
by updating the code in `version.extractSemVerVersion()`. The version is
specified in [VERSION](VERSION) and would also need to be updated.

### Multi-MAJOR version support

A new feature added in `v-6.1.0` is the ability for multiple `MAJOR` versions
to be changed. Until the feature was introduced once a new `MAJOR` version had
been released it was not possible to make changes to any previous version as
the version check in the build pipeline would fail the build.
This is problematic as previous versions often require bug fixes and on
occasion, new features need to be added for consumers that are either unable
to, or choose not to upgrade to use the latest version.
Checking the version has been incremented is critical in maintaining the
integrity of the library thus removing the check is not an option. Therefore,
the check has been updated to ensure the version of the SHA tagged with the
`MAJOR` version of the current `VERSION` file has been incremented e.g. if the
current branches `VERSION` file contains `v-9.8.7` the value of the `VERSION`
file in tag `v-9` will be used to compare.
If there is no pre-existing `MAJOR` version tag the check will succeed.
There is no check to ensure a new `MAJOR` version is being added in sequence.

**Note:** The change described above is the first step towards providing full
multi-version support. Currently there is no consideration for which branch the
change will be merged into and by default the changes will be merged into
`master`. This is not ideal due to the likely differences between `MAJOR`
versions. Therefore in order to fully support multiple versions new branches
will be required in order for those versions to co-exist. Currently this can be
achieved manually but will be automated at some point.

## Usage

### Default build configurations

A default build configuration is available for both Node.js and .NET Core
projects. Both configurations require an environment to be specified.
Specifying an environment means that in future we'll be able to
support pipelines for different clusters, such as staging and production
environments.

The .Net Core configuration additionally requires the name of the project to be
specified. The name of the project is the name of the csproj file that
represents the main project. Due to the nature of .NET Core projects the file
containing the version isn't able to be conventionally named and unlike a
Node.js project where `package.json` can be used the file name needs to be
specified.

Example Node.js Jenkinsfile:
```
@Library('defra-library@5') _

buildNodeJs environment: 'dev'
```

Example .NET Core Jenkinsfile:
```
@Library('defra-library@5') _

buildDotNetCore environment: 'dev', project: 'FFCDemoPaymentService'
```

Ideally, the default build will be suitable and can be used in place of
creating a bespoke build configuration via individual method calls.

However, should the default build configuration _not_ be suitable it is
possible to construct an entirely bespoke build configuration by making calls
to the individual methods within the global vars files. This approach
provides the most flexibility

Prior to exploring an entirely bespoke build configuration the option of using
pre-defined closures to supplement the default build should be considered.

### Pre-defined closure extension points

If your pipeline has additional steps, you can add closures to the config Map
passed to the pipeline that will be run as callbacks at various pre-defined
locations. The place where the callback is run is determined by the key
associated with the closure in the config Map. These are:

| Key               | Location                                                                |
| ---               | --------                                                                |
| `validateClosure` | After the stages to validate the pipeline should be run                 |
| `buildClosure`    | After the stages to lint the Helm chart and build the test image        |
| `testClosure`     | After the stages that run the tests                                     |
| `deployClosure`   | After the stages that build and publish the Helm and trigger the deploy |
| `failureClosure`  | At the end of the `catch` block when a pipeline build fails             |
| `finallyClosure`  | At the end of the `finally` block to clean up the build                 |

An example of passing closures to run in the finally and test block would look like:

```
def extraTestThings = {
  stage('Extra test things') {
    // Do the test things
  }
}

def extraFinallyThings = {
  stage('Extra finally things') {
    // Do the finally things
  }
}

buildNodeJs environment: 'dev',
            finallyClosure: extraFinallyThings,
            testClosure: extraTestThings
```

### Build configuration conventions

Both pipelines rely on a number of conventions being observed within your repo
and Jenkins setup:
1. The Helm chart must have the same name as the repository
2. The service name in Docker Compose must have the same name as the repository
3. The application must be deployed to a standard location (`/home/node` for
   node builds, `/home/dotnet` for .NET Core)
4. The repository must be hosted on GitHub
5. Your app should be hosted in an FFC cluster (only dev exists at present)
6. Your app must have separate deployment pipeline to trigger, with the name
   matching the pattern "$repoName-deploy" and a deploy token setup in Jenkins
   as a secret, again named "$repoName-deploy-token"
7. The application's Docker Compose files must be structured to support running
   tests with the command
   `docker-compose -f docker-compose.yaml -f docker-compose.test.yaml run $serviceName`
8. If the application contains liquibase migrations a compose file must be available
   that can be run with the command
   `docker-compose -f docker-compose.migrate.yaml database-up/database-down`

### Full bespoke build configuration

For scenarios when complete control over the build configuration is required,
individual methods can be used to construct the exact configuration necessary.
This section contains the details of how to do that and links to the
documentation for the available methods.

#### Create a bespoke build configuration

[Import the library](https://www.jenkins.io/doc/book/pipeline/shared-libraries/#using-libraries)
using the `@library` annotation, including an optional tag or branch.
SemVer tags are created for each release, so the `MAJOR`, `MINOR` or `PATCH`
version of a release can be targeted.

```
@Library('defra-library')
@Library('defra-library@master')
@Library('defra-library@3.1.2')
@Library('defra-library@3.1')
@Library('defra-library@3')
```

Once the library has been loaded any methods available within a
[global variable](https://www.jenkins.io/doc/book/pipeline/shared-libraries/#defining-global-variables)
can be used. For example if there is a script called `build.groovy` within the
`vars` directory and it contains a method with the name `masterBranch` it
could be called like this:

```
build.masterBranch()
```
For a more detailed example the [Jenkinsfile](Jenkinsfile) in this repo can be
used. There is no default build configuration for the library so it is made up
of calling methods as required.

## Available methods for build configuration

The groovy scripts within [vars](vars) are documented with a corresponding
`.md` file. There is also a corresponding `.txt` file, this contains basic html
which links back to the markdown file and will be rendered within the Jenkins
UI as per the
[documentation](https://www.jenkins.io/doc/book/pipeline/shared-libraries/#directory-structure).

An additional file to ease navigation within the directory is also provided -
[README](./vars/README.md)

### Deployment methods

A deployment configuration, `deployToCluster.groovy` is provided to support
deployment of specified Helm charts to specified clusters.

Example usage from Jenkins pipeline configuration.

```
deployToCluster environment: 'dev, namespace: 'ffc-demo', chartName: 'ffc-demo-web', chartVersion: '1.0.0'
```

## Setup Jenkins for use with the library

### Add global pipeline library

As per the
[documentation](https://www.jenkins.io/doc/book/pipeline/shared-libraries/#using-libraries)
the library needs to be setup as a global pipeline library. The name needs to
be set to `defra-library` (for the examples above to work). This feature is
enabled through the
[Shared Groovy Libraries](https://plugins.jenkins.io/workflow-cps-global-lib/)
plugin.

### Configure environment variables

A number of environment variables are used by the library. The following table
includes the list of variables that need to be setup as global properties.

| Name                             | Description                                                                                          | Value                                                               |
| ----                             | -----------                                                                                          | -----                                                               |
| `APP_CONFIG_NAME`                | Name of Azure App Config                                                                             | :see_no_evil:                                                       |
| `APP_CONFIG_SUBSCRIPTION`        | Name of subscription (in Azure) where the App Config resides                                         | :see_no_evil:                                                       |
| `DOCKER_REGISTRY`                | Domain of Docker registry where non-public images are pushed to and pulled from                      | :see_no_evil:                                                       |
| `DOCKER_REGISTRY_CREDENTIALS_ID` | Name of credential stored in Jenkins containing the credentials used to access the `DOCKER_REGISTRY` | :see_no_evil:                                                       |
| `HELM_CHART_REPO_PUBLIC`         | Path to the public Helm chart repository                                                             | https://raw.githubusercontent.com/defra/ffc-helm-repository/master/ |
| `HELM_CHART_REPO_TYPE`           | Type of repository where Helm charts are stored. Options are `acr` or `artifactory`                  | `acr`                                                               |
| `JENKINS_DEPLOY_SITE_ROOT`       | FQDN of Jenkins instance                                                                             | :see_no_evil:                                                       |
| `SNYK_ORG`                       | Name of Snyk organisation                                                                            | `defra-ffc`                                                         |
| `HELM_DEPLOYMENT_KEYS_FILENAME`  | Filename in Helm chart directory where deployment keys to pull from Azure App Config are listed      | `deployment-config-keys.txt`                                        |
