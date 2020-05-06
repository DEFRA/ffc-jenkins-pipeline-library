# secretScanner

> Below are the methods available on the script. They can be executed by
  calling `<script>.<method>` e.g. `secretScanner.scanFullHistory()`

## secretScanner.scanWithinWindow

Scans GitHub repos for potential secrets committed within a given time window.
It uses the [truffleHog](https://github.com/dxa4481/truffleHog) tool to scan
the commit history and branches of repos, running entropy and regex checks on
git diffs.
Given a GitHub username/organisation and a repo name prefix, this
method queries the GitHub API to identify matching repos and establish which of
these repos has had a commit within a given time window. `truffleHog` is then
run to identify potential secrets within these commits, and (optionally)
reports these to a Slack channel. A list of strings can be provided to exclude
from the secret detection (useful to exclude previously found false positives).

It takes seven parameters:
* githubCredentialId: string containing Jenkins credentialsId containing token
  for GitHub API access
* dockerImgName: string containing name of the docker image to run the tests
  with. It is assumed this image has been built and is accessible locally
* githubOwner: string containing owner of GitHub repos to be scanned e.g.
  'DEFRA'. Used in calls to the GitHub API e.g.
  https://api.github.com/users/:username/repos
* repositoryPrefix: string containing prefix of repo names to scan e.g. 'ffc'.
* scanWindowHrs: integer determining size of scanning window in hours
* excludeStrings: a list of strings to exclude from the secret detection. This
  is intended to be a list of false positives previously detected
* (Optional) slackChannel: name of the slack channel to report potential secret
  detection to. An empty string (the default) will disable slack reporting

The method returns `true` if potential secrets are detected, otherwise `false`.

## secretScanner.scanFullHistory

Scans full history GitHub repos for potential secrets committed. It uses the
[truffleHog](https://github.com/dxa4481/truffleHog) tool to scan the commit
history and branches of repos, running entropy and regex checks on git diffs.
Given a GitHub username/organisation and a repo name prefix, this method
queries the GitHub API to identify matching repos and runs `truffleHog` on each
repo to identify potential secrets. This method is designed for ad-hoc scanning
rather than as a scheduled job. Slack reporting is optional. A list of strings
can be provided to exclude from the secret detection (useful to exclude
previously found false positives).

It takes six parameters:
* githubCredentialId: string containing Jenkins credentialsId containing token
  for GitHub API access
* dockerImgName: string containing name of the docker image to run the tests
  with. It is assumed this image has been built and is accessible locally
* githubOwner: string containing owner of GitHub repos to be scanned e.g.
  'DEFRA'. Used in calls to the GitHub API e.g.
  https://api.github.com/users/:username/repos
* repositoryPrefix: string containing prefix of repo names to scan e.g. 'ffc'
* excludeStrings: a list of strings to exclude from the secret detection. This
  is intended to be a list of false positives previously detected
* (Optional) slackChannel: name of the slack channel to report potential secret
  detection to. An empty string (the default) will disable slack reporting

The method returns `true` if potential secrets are detected, otherwise `false`.
