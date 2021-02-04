# buildImage

> The default build configuration for building and publising and image to DockerHub

In order to use it with the default conventions simply add the following to
your Jenkinsfile, replacing the name of the project with your project's name.
For the details of what happens please review the
[buildImage](buildImage.groovy) script.

```
@Library('defra-library@v-9') _

buildImage
```

## Image tagging
By default, the image will be tagged with the version specified in a local `VERSION` folder.

If a suffix is needed to this tag, it can be supplied in the build configuration.

For example:

A repository named `ffc-sonar-dotnet-core` with a version of `1.0.0` would be tagged `ffc-sonar-dotnet-core:1.0.0`

If a suffix to the tag of `-dotnet3.1` was needed the below would achieve that.

```
buildImage tagSuffix: 'dotnet3.1'
```

## Default branch
The build will assume the default branch in the repository is named `main`.  If not the default can be supplied in the build configuration.

For example:

```
buildImage defaultBranch: 'master'
```
