### provisionPrSqsQueue / queues.provisionPrSqsQueue
This will provision sqs queues for a pull request, so any testing of the PR can be carried out in isolation.

Parameter details:
repoName - used to name the queue and the folder for tracking the queue in the git repo
prCode - the numeric code used for the PR, used to name the queue and the folder for tracking the queue in the git repo
queuePurpose - used to name the queue and the folder for tracking the queue in the git repo
serviceCode - three letter code for the workstream, e.g. 'FFC', used in the tags of the SQS queue, to identify the queue
serviceName - usually the same as the serviceCode, to be used in the tags of the SQS queue, to identify the queue
serviceType - a fuller description of the workstream, e.g. 'Future Farming Services', used in the tags of the SQS queue, to identify the queue

The queue is named in the format [repo name]-pr[pr code]-[queue purpose]. When combined, repo name, pr code and queue purpose shouldn't exceed 76 characters, this is a limit imposed by AWS.

### destroyPrSqsQueues / queues.destroyPrSqsQueues
This will tear down SQS queues provisioned by `provisionPrSqsQueue` and should be called when a PR is closed. It only requires the repo name and pr number and a single call to tear down all the queues that were created, which may have taken several calls to `provisionPrSqsQueue`
The repo name and pr code should be the same as the ones used for all queues created. If a different value was used for any queues (for whatever reason), that will necessitate a separate call to this method with the alternative values.
