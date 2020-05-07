# buildDotNetCore

> The default build configuration for a .NET Core project.

In order to use it with the default conventions simply add the following to
your Jenkinsfile, replacing the name of the project with your project's name.
For the details of what happens please review the
[buildDotNetCore](buildDotNetCore.groovy) script.

```
@Library('defra-library@5') _

buildDotNetCore environment: 'dev', project: 'FFCDemoDotNetCoreProjectName'
```
