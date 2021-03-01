# buildNpm

> The default build configuration for building and publising an npm module to npm

In order to use it with the default conventions simply add the following to
your Jenkinsfile, replacing the name of the project with your project's name.
For the details of what happens please review the
[buildImage](buildImage.groovy) script.

```
@Library('defra-library@v-9') _

buildNpm()
```

## Default branch
The build will assume the default branch in the repository is named `main`.  If not the default can be supplied in the build configuration.

For example:

```
buildNpm defaultBranch: 'master'
```
