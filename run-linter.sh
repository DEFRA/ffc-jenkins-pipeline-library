#!/usr/bin/env sh

# Run [GitHub Super-Linter](https://github.com/github/super-linter/blob/master/docs/run-linter-locally.md) locally to lint Groovy code
docker run -e RUN_LOCAL=true -v "$(pwd)"/src/:/tmp/lint github/super-linter
