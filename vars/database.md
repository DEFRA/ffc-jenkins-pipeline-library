# database

> Database related methods

Below are the methods available on the script. They can be executed by calling
`<script>.<method>` e.g. `database.destroyPrDatabaseRoleAndSchema()`

## destroyPrDatabaseRoleAndSchema

Drops a Postgres database PR role and schema. See
[associated confluence page](https://eaflood.atlassian.net/wiki/spaces/FPS/pages/1596653973/Creating+PR+database+namespaces+in+postgres)
for context.

It takes four parameters:
- a string describing the Jenkins secret text credentials ID that contains
  database host to connect to
- a string describing the database name containing the schema
- a string describing the Jenkins usernamePassword credentials ID that contains
  the database user and password
- a string describing the PR number, e.g. `53`

## provisionPrDatabaseRoleAndSchema

Creates a Postgres database role and schema for use by a PR. See
[associated confluence page](https://eaflood.atlassian.net/wiki/spaces/FPS/pages/1596653973/Creating+PR+database+namespaces+in+postgres)
for context.

It takes six parameters:
- a string describing the Jenkins secret text credentials ID that contains
  database host to connect to
- a string describing the database name that the schema will be created in
- a string describing the Jenkins usernamePassword credentials ID that contains
  the database user and password
- a string describing the Jenkins usernamePassword credentials ID that contains
  the password for the role that will be created
- a string describing the PR number, e.g. `53`
- (optional, default=`false`) a boolean to determine if the SQL commands to
  create the PR role and schema include the `IF NOT EXISTS` logic. If `true`
  the SQL commands **will not** error if the schema and/or role already exist.
  If `false` (the default) the SQL commands **will** error if the schema and/or
  role already exist and the pipeline will exit.

It returns a list containing:
- the PR schema created
- the PR role created
