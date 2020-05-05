# Jenkins pipeline library

## Overview

Common scripts for use in Jenkins pipelines used in build and deployment.

## Versioning

The library is versioned following the
[semantic versioning specification](https://semver.org/). When updating the
library you will need to increment the version number in the `VERSION` file.
Upon merge to the master branch a new GitHub release will automatically be
created, tagged with the `MAJOR`, `MINOR` and `PATCH` versions. For example
version `2.4.6` will have the tags `2`, `2.4` and `2.4.6`.

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

### Deployment pipeline
A deployment pipeline, `deployToCluster.groovy` is provided to support deployment of specified Helm charts to specified clusters.

Example usage from Jenkins pipeline configuration.

```
deployToCluster environment: 'dev, namespace: 'ffc-demo', chartName: 'ffc-demo-web', chartVersion: '1.0.0'
```

## Testing

A simple test harness may be run to unit test methods that are purely `groovy` code. This uses a groovy docker image and may be run via
```
./scripts/test
```

## Available methods

The methods are documented within the `vars` directory as Markdown files with a
`.txt` extension so the documentation is available on the Jenkins server. See
the
[official documentation](https://www.jenkins.io/doc/book/pipeline/shared-libraries/#directory-structure)
for additional information.
