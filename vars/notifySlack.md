# notifySlack

> Slack notification related methods

Below are the methods available on the script. They can be executed by calling
`<script>.<method>` e.g. `notifySlack.buildFailure()`

## buildFailure

Sends a message to the ffc-notifications Slack workspace when the Jenkins build
fails.

Takes two parameters:
- optional channel name for main branch build failures to be reported in,
  must contain the # before the channel name e.g. `generalbuildfailures`
- name of the default branch

Example usage:

```
notifySlack.buildFailure("generalbuildfailures", 'main')
```

## sendMessage

Sends a given message to a given channel in the the ffc-notifications Slack
workspace. It can be parameterised to add the `@here` annotation.

It takes three parameters:

* channel: a string containing the channel name e.g. generalbuildfailures"
* message: a string containing the message to send
* useHere: a boolean determining whether to prefix the message with the `@here`
  annotation or not
