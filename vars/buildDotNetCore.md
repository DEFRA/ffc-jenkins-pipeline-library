# buildDotNetCore

> The default build configuration for a .NET Core project.

In order to use it with the default conventions simply add the following to
your Jenkinsfile, replacing the name of the project with your project's name.
For the details of what happens please review the
[buildDotNetCore](buildDotNetCore.groovy) script.

```
@Library('defra-library@v-8') _

buildDotNetCore environment: 'dev', project: 'FFCDemoDotNetCoreProjectName'
```

## Synk

If Snyk analysis is required a file named `docker-compose.snyk.yaml` must be present in the project root.
The `docker-compose.snyk.yaml` script should move files required by Snyk from the built image to the `obj` folder.

An example can be seen in [ffc-demo-payment-service-core](https://github.com/DEFRA/ffc-demo-payment-service-core)

The `Snyk test` stage will ensure the `obj` folder is writable by Docker to allow file extraction.

## Default branch
The build will assume the default branch in the repository is named `main`.  If not the default can be supplied in hte build configuration.

For example:

```
buildDotNetCore environment: 'dev', project: 'FFCDemoDotNetCoreProjectName', defaultBranch: 'master'
```
