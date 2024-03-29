# buildNodeJs

> The default build configuration for a Node.js project.

In order to use it with the default conventions simply add the following to
your Jenkinsfile.
For the details of what happens please review the
[buildNodeJs](buildNodeJs.groovy) script.

```
@Library('defra-library@v-9') _

buildNodeJs()
```

## npm Audit

By default an [npm audit](https://docs.npmjs.com/cli/audit) will run for every
build. If any issues are identified at a level of `moderate` or above the build
will be marked as failed for PR builds only. When the step runs during a build
of the main branch it will warn only.

The job has been setup to allow several options to be configured, details of
those options are available in [build](build.md). In order to override the
options when running the `buildNodeJs` pipeline, the config option requires the
keys `npmAuditLevel`, `npmAuditLogType` and `npmAuditFailOnIssues`.
An example overriding the default values:

```
buildNodeJs npmAuditLevel: 'low', npmAuditLogType: 'json', npmAuditFailOnIssues: true
```

## Synk

By default a
[snyk test](https://support.snyk.io/hc/en-us/articles/360003812578#UUID-c88e66cf-431c-9ab1-d388-a8f82991c6e0)
will run during the build. This is achieved through the use of the
[snyk security scanner](https://plugins.jenkins.io/snyk-security-scanner/)
plugin for Jenkins.
The default settings will fail the build when an issue of `medium` or above is
identified on PR builds only. When the step runs during a build of the main
branch it will warn only.
The job has been setup to allow several options to be configured, details of
those options are available in [build](build.md). In order to override the
options from the `buildNodeJs` pipeline the config object requires the keys
`snykFailOnIssues`, `snykOrganisation` and `snykSeverity`.
An example overriding the default values:

```
buildNodeJs environment: 'dev', snykFailOnIssues: true, snykOrganisation: 'my-org-name', snykSeverity: 'high'
```

## Default branch
The build will assume the default branch in the repository is named `main`.  If not the default can be supplied in the build configuration.

For example:

```
buildNodeJs defaultBranch: 'master'
```

## Environment

As part of the PR workflow, every commit will be dynamically deployed to the default Sandpit cluster, `snd`.  This also applied to all main branch code post merge.  
If a different environment within the Sandpit environment should be used for this purpose then the default can be overridden.

For example:

```
buildNodeJs environment: 'snd2'
```

This main branch deployment can be disabled by setting the `triggerDeployment` value to `false`.

For example:

```
buildNodeJs triggerDeployment: false
```

The build will assume the deployment pipeline has the naming convention `<repository name>-deploy`.  This can be overridden for custom naming conventions.

For example:

```
buildNodeJs deploymentPipelineName: 'my-deployment-pipeline'
```

## Support Image without Helm

The pipeline supports images that do not need to target Kubernetes. The pipeline checks for the existence of a helm folder. If this folder does not exist, the helm steps are ignored.
