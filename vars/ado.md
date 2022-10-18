# ado

> Azure DevOps related methods

Below are the methods available on the script. They can be executed by calling
`<script>.<method>` e.g. `ado.triggerPipeline()`

## triggerPipeline

Triggers an Azure DevOps pipeline.

Takes two parameters
- the default branch to use if one is not supplied, e.g. `main`
- optional override to the default
