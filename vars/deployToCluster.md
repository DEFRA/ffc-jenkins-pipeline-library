# deployToCluster

> The default deploy to cluster build configuration

`deployToCluster` represents a default build configuration for deploying a Helm
chart to the SND Kubernetes cluster and subsequently triggering the Azure DevOps pipeline to production.

Below is an example of how this can be achieved.

```
@Library('defra-library@v-6') _

deployToCluster environment: 'snd', namespace: 'ffc-demo', chartName: 'ffc-demo-web', chartVersion: '1.0.0', helmChartRepoType: 'acr'
```

If `environment` is not supplied then the deployment to Kubernetes will be skipped and the pipeline will only trigger the Azure DevOps pipeline.
