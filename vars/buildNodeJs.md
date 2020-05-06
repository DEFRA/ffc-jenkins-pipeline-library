# buildNodeJs

> `buildNodeJs` represents the default (and some would say the standard)
  build configuration for a Node.js project. In order to use it with the
  default conventions simply add the following to your Jenkinsfile.

```
@Library('defra-library@5') _

buildNodeJs environment: 'dev'
```
