# ado

> Azure DevOps related methods

Below are the methods available on the script. They can be executed by calling
`<script>.<method>` e.g. `ado.triggerPipeline()`

## triggerPipeline

Triggers Azure DevOps pipeline for Kubernetes deployments.

Takes four parameters:
- target namespace <String>
- Helm chart name <String>
- Helm chart version <String>
- whether the application has a database <Boolean>

