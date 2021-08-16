# buildHelm

> The default build configuration for a project with a common helm chart without an Image.

In order to use it with the default conventions simply add the following to
your Jenkinsfile.
For the details of what happens please review the
[buildHelm](buildHelm.groovy) script.

```
@Library('defra-library@v-9') _

buildHelm()
```

## npm Audit

By default an [npm audit](https://docs.npmjs.com/cli/audit) will run for every
build. If any issues are identified at a level of `moderate` or above the build
will be marked as failed for PR builds only. When the step runs during a build
of the main branch it will warn only.

The job has been setup to allow several options to be configured, details of
those options are available in [build](build.md). In order to override the
options when running the `buildHelm` pipeline, the config option requires the
keys `npmAuditLevel`, `npmAuditLogType` and `npmAuditFailOnIssues`.
An example overriding the default values:

```
buildNodeJs npmAuditLevel: 'low', npmAuditLogType: 'json', npmAuditFailOnIssues: true
```

## Default branch
The build will assume the default branch in the repository is named `main`.  If not the default can be supplied in hte build configuration.

For example:

```
buildHelm defaultBranch: 'master'
```

## Environment

As part of the PR workflow, every commit will be dynamically deployed to the default Sandpit cluster, `snd`.  This also applied to all main branch code post merge.
If a different environment within the Sandpit environment should be used for this purpose then the default can be overriden.

For example:

```
buildHelm environment: 'snd2'
```

This main branch deployment can be disabled by setting the `triggerDeployment` value to `false`.

For example:

```
buildHelm triggerDeployment: false
```

The build will assume the deployment pipeline has the naming convention `<repsitory name>-deploy`.  This can be overridden for custom naming conventions.

For example:

```
buildHelm deploymentPipelineName: 'my-deployment-pipeline'
```
