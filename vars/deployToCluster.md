# deployToCluster

> The default deploy to cluster build configuration

`deployToCluster` represents a default build configuration for deploying a Helm
chart to a Kubernetes cluster. Below is an example of how this can be achieved.

```
@Library('defra-library@5') _

deployToCluster environment: 'dev, namespace: 'ffc-demo', chartName: 'ffc-demo-web', chartVersion: '1.0.0'
```
