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

<<<<<<< HEAD
By default an [npm audit](https://docs.npmjs.com/cli/audit) will run for every
build. Currently the job will not fail the build regardless of any
vulnerabilities being found. This is a temporary measure and when it changes,
the library will be a new `MAJOR` version as there is a high likelihood
existing builds will fail.
The job has been setup to allow several options to be configured, details of
those options are available in [build](build.md). In order to override the
options when running the `buildNodeJs` pipeline, the config option requires the
keys `npmAuditLevel`, `npmAuditLogType` and `npmAuditFailOnIssues`.
An example overriding the default values:

```
buildNodeJs environment: 'dev', npmAuditLevel: 'low', npmAuditLogType: 'json', npmAuditFailOnIssues: true
=======
By default a
[snyk test](https://support.snyk.io/hc/en-us/articles/360003812578#UUID-c88e66cf-431c-9ab1-d388-a8f82991c6e0)
will run during the build. This is achieved through the use the
[snyk security scanner](https://plugins.jenkins.io/snyk-security-scanner/)
plugin for Jenkins.
Currently the default settings will not fail the build if vulnerabilities are
detected, however, this is likely to change in the future (a MAJOR version
increment to the library will be made when this happens).
The job has been setup so both options `failOnIssues` and `organisation`
can be configured via the config parameter via the `snykFailOnIssues` and
`snykOrganisation` keys, respectively.
Below shows an example where both values are set:
```
buildNodeJs environment: 'dev', snykFailOnIssues: true, snykOrganisation: 'my-org-name'
>>>>>>> bb29a9a... docs: add documentation for snyk test job
```
