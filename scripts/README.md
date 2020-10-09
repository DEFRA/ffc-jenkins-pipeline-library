# Scripts

Directory contains scripts to ease the running of stuff. This README provides a
high level overview of what each script does. Additional details can be found
by viewing the scripts directly.

## [commitlint](commitlint)

Runs `commitlint`on an individual commit message using globally installed
[@commitlint/cli](https://www.npmjs.com/package/@commitlint/cli) and
[@commitlint/config-conventional](https://www.npmjs.com/package/@commitlint/config-conventional).

Used by the `commit-msg` Git hook (if setup).

## [commitlint-branch](commitlint-branch)

Builds a Docker image from [commitlint.Dockerfile](../commitlint.Dockerfile)
and runs it to run `commitlint` on all commits on the current branch.

Used during the build to ensure all commit message are of the appropriate
conventional commit standard.
Can be run locally but generally shouldn't be required as all commits should be
individually linted through the use of the `commit-msg` Git hook..

## [run-github-super-linter](run-github-super-linter)

Runs [GitHub Super-Linter](https://github.com/github/super-linter/blob/master/docs/run-linter-locally.md).

Used during the build. Can be run locally.

## [semantic-release](semantic-release)

Builds a Docker image from
[semantic-release.Dockerfile](../semantic-release.Dockerfile)
and runs it to run `semantic-release`.

Used during the build to create a release. The details are available in
[.releaserc.json](../.releaserc.json)
