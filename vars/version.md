# version

> Below are the methods available on the script. They can be executed by
  calling `<script>.<method>` e.g. `version.getFileVersion()`

## versionHasIncremented

Method used internally in the `verifyCSProjVersionIncremented`,
`verifyPackageJsonVersionIncremented` and `verifyFileVersionIncremented`
method.

Takes two parameters of the versions to compare, typically master version and
branch version.

The method returns `true` if both versions are valid SemVers, and the second
version is higher than the first.
The method returns `false` if either version is invalid, or the second version
is not higher than the first.

## errorOnNoVersionIncrement

Convenience method shared by `verifyPackageJsonVersionIncremented` and
`verifyCSProjVersionIncremented` to throw error when the version has not been
incremented, or is invalid.

Takes two parameters - master version and branch version.

## getCSProjVersion

Returns the project version from the `[projectName].csproj` file. It requires
the project name to be passed as a parameter, but this means that in a solution
of several projects, versions can be retrieved for each of them.

## getCSProjVersionMaster

Returns the project version from the `[projectName].csproj` file in the master
branch. It requires the project name to be passed as a parameter, but this
means that in a solution of several projects, versions can be retrieved for
each of them.

## verifyCSProjVersionIncremented

Compares the master version with the branch version from the provided project
name. If the version has been incremented correctly a message will be `echoed`
displaying the new and the old version, i.e.
`version increment valid '1.0.0' -> '1.0.1'`.

If the version has not incremented correctly, or is invalid, an error will be
thrown containing the new and the old versions, i.e.
`version increment invalid '1.0.0' -> '1.0.0'`.

The method requires the project name to be passed as a parameter.

## getPackageJsonVersion

Returns the package version from the `package.json` file.

## getPackageJsonVersionMaster

Returns the package version from the `package.json` file in the master branch.

## verifyPackageJsonVersionIncremented

Compares the master version with the branch version of the `package.json`.
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

## getFileVersionMaster

Returns the contents of a given file in the master branch. The assumption is
that this file contains a single string that is a SemVer formatted version
number: `MAJOR.MINOR.PATCH`.

It takes one parameter:
* a string containing the file name of the file containing the version number

## verifyFileVersionIncremented

Compares the master version with the branch version defined in a given file.
If the version has been incremented correctly message will be `echoed`
displaying the new and the old version, i.e.

`version increment valid '1.0.0' -> '1.0.1'`.

If the version has not incremented correctly, or is invalid, an error will be
thrown containing the new and the old versions, i.e.

`version increment invalid '1.0.0' -> '1.0.0'`.

It takes one parameter:
* a string containing the file name of the file containing the version number
