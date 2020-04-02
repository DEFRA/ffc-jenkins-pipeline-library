import uk.gov.defra.ffc.GitHub
def gh = new GitHub()

def setStatusSuccess(message = 'Build successful') {
  gh.updateCommitStatus(message, 'SUCCESS')
}

def setStatusPending(message = 'Build started') {
  gh.updateCommitStatus(message, 'PENDING')
}

def setStatusFailure(message = '') {
  gh.updateCommitStatus(message, 'FAILURE')
}
