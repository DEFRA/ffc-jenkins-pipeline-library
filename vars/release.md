# release

> Release related methods

Below are the methods available on the script. They can be executed by calling
`<script>.<method>` e.g. `release.tagCommit()`

## addSemverTags

Attaches a `MAJOR` and `MINOR` version tag to the latest commit to a repo in
the DEFRA GitHub account. It uses `tagCommit` to perform the commit tagging on
origin.

Examples:
* provided with a `PATCH` increment to a version (e.g. `2.3.5` to `2.3.6`), the
  `MAJOR` and `MINOR` tags (`2` and `2.3`) will be moved to the latest commit.
* provided with a `MINOR` increment to a version (e.g. `2.3.5` to `2.4.0`), the
  `MAJOR` tag (`2`) will be moved and the `MINOR` tag (`2.4`) will be created
  and attached to the latest commit.
* provided with a `MAJOR` increment to a version (e.g. `2.3.5` to `3.0.0`), the
  `MAJOR` and `MINOR` tags (`3` and `3.0`) will be created and attached to the
  latest commit.

It takes two parameters:
* a string containing the current SemVer version for the release formatted as
  `MAJOR.MINOR.PATCH`
* a string containing the name of the repository (assumed to be in the DEFRA
  GitHub account)

## trigger

Triggers a release to be created on GitHub for a given repo only where a
release with the identical SemVer does not already exist

Takes four parameters:
- the version tag in SemVer format to check for on GitHub e.g 1.0.0
- the repository name to check
- the release description text
- the GitHub connection token secret text

```
release.trigger(versionTag, repoName, releaseDescription, token)
```
