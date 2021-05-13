# buildDotNetCore

> The default build configuration for a .NET Core project.

In order to use it with the default conventions simply add the following to
your Jenkinsfile, replacing the name of the project with your project's name.
For the details of what happens please review the
[buildDotNetCore](buildDotNetCore.groovy) script.

```
@Library('defra-library@v-8') _

buildDotNetCore project: 'FFCDemoDotNetCoreProjectName'
```

## Synk

If Snyk analysis is required a file named `docker-compose.snyk.yaml` must be present in the project root.
The `docker-compose.snyk.yaml` script should move files required by Snyk from the built image to the `obj` folder.

An example can be seen in [ffc-demo-payment-service-core](https://github.com/DEFRA/ffc-demo-payment-service-core)

The `Snyk test` stage will ensure the `obj` folder is writable by Docker to allow file extraction.

## Default branch
The build will assume the default branch in the repository is named `main`.  If not the default can be supplied in the build configuration.

For example:

```
buildDotNetCore project: 'FFCDemoDotNetCoreProjectName', defaultBranch: 'master'
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

## Support Image without Helm

The pipeline supports images that do not need to target Kubernetes. The pipeline checks for the existence of a helm folder. If this folder does not exist, the helm steps are ignored.
