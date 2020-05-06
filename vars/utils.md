# utils

> Below are the methods available on the script. They can be executed by
  calling `<script>.<method>` e.g. `utils.replaceInFile()`

## getCommitMessage

Return the message of the latest commit for the current repository.

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

Example usage to replace the path `/usr/src/app` with `./app` a path in the file `./lcov.info`:

```
utils.replaceInFile('\\/usr\\/src\\/app', '.\\/app', './lcov.info')
```
