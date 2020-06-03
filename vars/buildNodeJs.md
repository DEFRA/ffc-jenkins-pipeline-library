# buildNodeJs

> The default build configuration for a Node.js project.

In order to use it with the default conventions simply add the following to
your Jenkinsfile.
For the details of what happens please review the
[buildNodeJs](buildNodeJs.groovy) script.

```
@Library('defra-library@5') _

buildNodeJs environment: 'dev'
```

By default an [npm audit](https://docs.npmjs.com/cli/audit) will run for every
build. Currently the job will not fail the build regardless of any
vulnerabilities being found. This is a temporary measure and when it changes,
the library will be a new `MAJOR` version as there is a high likelihood
existing builds will fail.
The job has been setup to allow several options to be configured, details of
those options are available in [build](build.md). In order to override the
options when running the `buildNodeJs` pipeline, the config option requires the
keys `npmAuditLevel` and `npmLogType`.
An example overriding the default values:

```
buildNodeJs environment: 'dev', npmAuditLevel: 'low', npmLogType: 'json'
```
