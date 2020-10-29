# version

> Methods related to versioning of the repository/image/artifact

Below are the methods available on the script. They can be executed by calling
`<script>.<method>` e.g. `version.getFileVersion()`

## getCSProjVersion

Returns the project version from the `[projectName].csproj` file. It requires
the project name to be passed as a parameter, but this means that in a solution
of several projects, versions can be retrieved for each of them.


## verifyCSProjIncremented

Compares the main version with the branch version from the provided project
name. If the version has been incremented correctly a message will be `echoed`
displaying the new and the old version, i.e.
`version increment valid '1.0.0' -> '1.0.1'`.

If the version has not incremented correctly, or is invalid, an error will be
thrown containing the new and the old versions, i.e.
`version increment invalid '1.0.0' -> '1.0.0'`.

The method requires the project name to be passed as a parameter.

## getPackageJsonVersion

Returns the package version from the `package.json` file.

## verifyPackageJsonIncremented

Compares the main version with the branch version of the `package.json`.
If the version has been incremented correctly message will be `echoed`
displaying the new and the old version, i.e.
`version increment valid '1.0.0' -> '1.0.1'`.

If the version has not incremented correctly, or is invalid, an error will be
thrown containing the new and the old versions, i.e.
`version increment invalid '1.0.0' -> '1.0.0'`.

## getFileVersion

Returns the contents of a given file. The assumption is that this file contains
a single string that is a SemVer formatted version number: `MAJOR.MINOR.PATCH`.

It takes one parameter:
* a string containing the file name of the file containing the version number

## verifyFileIncremented

Compares the main version with the branch version defined in a given file.
If the version has been incremented correctly message will be `echoed`
displaying the new and the old version, i.e.

`version increment valid '1.0.0' -> '1.0.1'`.

If the version has not incremented correctly, or is invalid, an error will be
thrown containing the new and the old versions, i.e.

`version increment invalid '1.0.0' -> '1.0.0'`.

It takes one parameter:
* a string containing the file name of the file containing the version number
