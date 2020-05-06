# buildDotNetCore

> `buildDotNetCore` represents the default (and some would say the standard)
  build configuration for a .NET Core project. In order to use it with the
  default conventions simply add the following to your Jenkinsfile, replacing
  the name of the project with your project's name.

```
@Library('defra-library@5') _

buildDotNetCore environment: 'dev', project: 'FFCDemoDotNetCoreProjectName'
```
