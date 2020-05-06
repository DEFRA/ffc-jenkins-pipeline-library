# helm

> Below are the methods available on the script. They can be executed by
  calling `<script>.<method>` e.g. `helm.deployChart()`

## deployChart

Deploys the Helm chart stored in the repository to Kubernetes.
By convention Helm charts are stored in the folder `helm` in a subfolder the
same name as the image, service, and repository.

Development Helm charts are deployed with the name and namespace set to a
combination of the image name and tag, i.e. `ffc-demo-web-pr53`

Takes four parameters:
- environment to deploy to (this is used to determine which K8s credentials to
  use)
- the registry where the chart's image is stored
- the name of the chart (note this must also be the name of the image to deploy)
- container image tag to deploy

Example usage:

```
helm.deployChart('dev', 'myregistry.mydockerhub.com', 'ffc-demo-web', 'pr53')
```

## undeployChart

Removes a Helm chart previously deployed to a Kubernetes cluster.
Both the chart and the namespace are removed when the chart is undeployed.

Takes three parameters:
- The ID of the Kubernetes credentials previously setup in Jenkins
- the name of the chart
- container image tag that was deployed by this chart

Example usage:

```
helm.undeployChart('kubeconfig01', 'ffc-demo-web', 'pr53')
```

## undeployChart

Removes a Helm chart previously deployed to a Kubernetes cluster.
Both the chart and the namespace are removed when the chart is undeployed.

Takes three parameters:
- environment to undeploy from
- the name of the chart
- container image tag that was deployed by this chart

Example usage:

```
helm.undeployChart('dev', 'ffc-demo-web', 'pr53')
```

## publishChart

Publishes the local Helm chart to an Artifactory Helm chart repository.

Takes three parameters:
- docker registry without the protocol
- chart name
- container image tag

Uses the environment variable `HELM_CHART_REPO_PUBLIC` (set within Jenkins)
to provide the location of a packaged library chart.

Example usage:

```
helm.publishChart('myregistry.mydockerhub.com', 'ffc-demo-web', 'master')
```

## deployRemoteChart

Deploys a Helm chart from a remote chart repository to Kubernetes.

Takes four parameters:
- the Kubernetes namespace to deploy into
- the chart name
- the chart version
- additional command line parameters to send to the Helm deployment

Example usage:

```
def extraCommands = "--values ./helm/ffc-demo-web/aws-values.yaml"
helm.deployRemoteChart('ffc-demo', 'ffc-demo-web', '1.1.7', extraCommands)
```

## deployRemoteChart

Deploys a Helm chart from a remote chart repository (Artifactory) to Kubernetes.

Takes four parameters:
- the environment to deploy into
- the Kubernetes namespace to deploy into
- the chart name
- the chart version

The Artifactory repository is specified by an env var (set in Jenkins) -
`ARTIFACTORY_REPO_URL`.

Example usage:

```
helm.deployRemoteChart('dev', 'ffc-demo', 'ffc-demo-web', '1.1.7')
```
