package uk.gov.defra

import uk.gov.defra.Utils
def utils = new Utils()

def serviceName = 'ffc-jenkins-pipeline-library'
def versionFileName = 'VERSION'

def updateCommitStatus(message, state) {
  def version = utils.getFileVersion(versionFileName)
  (pr, containerTag, mergedPrNo, repoUrl, commitSha) = utils.getVariables(serviceName, version)
  step([
    $class: 'GitHubCommitStatusSetter',
    reposSource: [$class: "ManuallyEnteredRepositorySource", url: repoUrl],
    commitShaSource: [$class: "ManuallyEnteredShaSource", sha: commitSha],
    errorHandlers: [[$class: 'ShallowAnyErrorHandler']],
    statusResultSource: [ $class: "ConditionalStatusResultSource", results: [[$class: "AnyBuildResult", message: message, state: state]] ]
  ])
}
