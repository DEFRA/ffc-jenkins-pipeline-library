# buildFunctionDotNet

> The default build configuration for a Azure Function Node.js project.

In order to use it with the default conventions simply add the following to
your Jenkinsfile.
For the details of what happens please review the
[buildDotNetFunction](buildDotNetFunction.groovy) script.

```
@Library('defra-library@v-9') _

buildDotNetFunction project: 'FFCDemoDotNetProjectName'
```

## Default branch
The build will assume the default branch in the repository is named `main`.  If not the default can be supplied in the build configuration.

For example:

```
buildDotNetFunction defaultBranch: 'master'
```

## Environment

As part of the PR workflow, every commit will be dynamically deployed to the default Sandpit cluster, `snd`.  This also applied to all main branch code post merge.  
If a different environment within the Sandpit environment should be used for this purpose then the default can be overriden.

For example:

```
buildDotNetCore project: 'FFCDemoDotNetCoreProjectName', environment: 'snd2'
```

This main branch deployment can be disabled by setting the `triggerDeployment` value to `false`.

For example:

```
buildDotNetCore project: 'FFCDemoDotNetCoreProjectName', triggerDeployment: false
```

The build will assume the deployment pipeline has the naming convention `<repsitory name>-deploy`.  This can be overridden for custom naming conventions.

For example:

```
buildDotNetCore project: 'FFCDemoDotNetCoreProjectName', deploymentPipelineName: 'my-deployment-pipeline'
```
