# Scripts

Scripts to ease the running of stuff. This README provides a high level
overview of what each script does. If you are interested in the details it is
best to check the contents of the scripts.


## [commitlint](commitlint)

Runs `commitlint`on an individual commit message using globally installed
[@commitlint/cli](https://www.npmjs.com/package/@commitlint/cli) and
[@commitlint/config-conventional](https://www.npmjs.com/package/@commitlint/config-conventional).

It is used by the `commit-msg` Git hook (if setup).

## [commitlint-branch](commitlint-branch)

Builds a Docker image from [commitlint.Dockerfile](../commitlint.Dockerfile)
and runs it to run `commitlint` on all commits on the current branch.

Is used during the build to ensure all commit message are of the appropriate
conventional commit standard.
Can be run locally but generally shouldn't be required as all commits should be
individual linted through the use of the `commit-msg` Git hook..

## run-github-super-linter

Runs [GitHub Super-Linter](https://github.com/github/super-linter/blob/master/docs/run-linter-locally.md).

Is used during the build and can be run locally.

## semantic-release

Builds a Docker image from
[semantic-release.Dockerfile](../semantic-release.Dockerfile)
and runs it to run `semantic-release`.

Is used during the build to create a release. The details of are available
in [.releaserc.json](../.releaserc.json)
