# deploy

> Methods related to the deployment of the local (just built) image

Below are the methods available on the script. They can be executed by calling
`<script>.<method>` e.g. `deploy.trigger()`

## trigger

Triggers the Jenkins deployment job to deploy the built image.

Takes four parameters:
- the full URL (without trailing /) of the Jenkins service, including username
  and access token e.g.
  `https://deploy:accesstoken@jenkins.ffc.domain.in.the.defra.cloud`
- the Jenkins job name for the deploy job, this is in the URL when you edit or
  view the job in Jenkins
- the token that is set up when you configured the job in Jenkins. You must
  tick the "Trigger builds remotely" option when configuring the job. The
  Authentication token entered into the job is the one that should be passed
  here
- an object, that should contain all the parameters that need to be passed to
  the job (if required), for example `['version': '1.0.0']`

Example usage:

```
deploy.trigger((jenkinsDeployUrl, deployJobName, jenkinsToken, ['chartVersion':'1.0.0'])
```
