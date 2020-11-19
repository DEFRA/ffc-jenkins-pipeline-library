import uk.gov.defra.ffc.Utils

def replaceInFile(String from, String to, String file) {
  Utils.replaceInFile(this, from, to, file)
}

def getCommitMessage() {
  return Utils.getCommitMessage(this)
}

def getCommitSha() {
  return Utils.getCommitSha(this)
}

def getRepoName() {
  return Utils.getRepoName(this)
}

def getErrorMessage(e) {
  return Utils.getErrorMessage(e)
}
