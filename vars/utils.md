# utils

> General utility type methods that do not fit within other scripts

Below are the methods available on the script. They can be executed by calling
`<script>.<method>` e.g. `utils.replaceInFile()`

## getCommitMessage

Return the message of the latest commit for the current repository.

## getRepoName

Return the name for the current git repository.

## getCommitSha

Return the SHA hash of the latest commit for the current repository, i.e.
`9fd0a77d3eaa3d4370d3f31158f37dd8abd19fae`

## getErrorMessage

Return `message` from the passed error argument, if it exists. If there is no
`message` property the argument will be checked for `getCauses`. If there are
causes these will be returned in a comma separated string. If there are no
causes the default message will be returned i.e. `No error message available.`.

## replaceInFile

Utility method to globally substitute text in a file.

The method takes three parameters:
- `from`: the text to match
- `to`: the new text to replace the matched text
- `file`: path to file

The `from` and `to` values are used directly in a sed command, so wildcards may
be used.

It can be tricky to correctly escape characters for sed. For example the
forward slash `/` needs to be escaped with a back slash, which itself need to
be escaped with a back slash so the valid encoding for `/` is  `\\/`.

Forward slashes in the final `file` parameter do not need escaping.

Example usage to replace the path `/usr/src/app` with `./app` a path in the
file `./lcov.info`:

```
utils.replaceInFile('\\/usr\\/src\\/app', '.\\/app', './lcov.info')
```
