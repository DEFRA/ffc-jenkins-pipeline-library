# buildNodeJs

> The default build configuration for a Node.js project.

In order to use it with the default conventions simply add the following to
your Jenkinsfile.
For the details of what happens please review the
[buildNodeJs](buildNodeJs.groovy) script.

```
@Library('defra-library@v-8') _

buildNodeJs environment: 'dev'
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
buildNodeJs environment: 'dev', npmAuditLevel: 'low', npmAuditLogType: 'json', npmAuditFailOnIssues: true
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
The build will assume the default branch in the repository is named `main`.  If not the default can be supplied in hte build configuration.

For example:

```
buildNodeJs environment: 'dev', defaultBranch: 'master'
```
