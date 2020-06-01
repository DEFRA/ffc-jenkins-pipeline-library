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

By default an [npm audit](https://docs.npmjs.com/cli/audit) will run and
fail the build if there are any vulnerabilities at `moderate` level or above.
It is possible to override the level via the `npmAuditLevel` config parameter.
The audit level is not validated beyond the validation npm audit performs.
Currently, acceptable levels are `low`, `moderate` and `high`. Example setting
level to fail at as `high` -
`buildNodeJs environment: 'dev' npmAuditLevel: 'high'`.
