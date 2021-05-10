# function

> provision Azure function app resources

For the details, review the
[function](function.groovy) script.

## createFunctionResources

Creates Azure function app resources.

Creates the following resources:
- storage account
- Azure function app

Takes one parameter:
- repository name

Example usage:
```
 stage('Provision function app') {
        function.createFunctionResources(repoName)
      }