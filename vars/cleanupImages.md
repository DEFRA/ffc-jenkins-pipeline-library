# Cleanup

> Methods related to cleaning up dangling images from Azure Container Registry

Publishing a new image to an existing tag results in the original image becoming orphaned or "dangling".

This happens regularly in FFC as in development Pull Request tags are overwritten within each feature branch build.

As recommended by [Microsoft](https://docs.microsoft.com/en-us/azure/container-registry/container-registry-delete#delete-untagged-images), these images need to be regularly cleaned up to maintain the size of the repository.

To use this pipeline, a Jenkins job should be created to run the following script:
```
@Library('defra-library') _

cleanupImages()
```

Note: The library may be set to a specific version with the `@` suffix, e.g.
`defra-library@9`
