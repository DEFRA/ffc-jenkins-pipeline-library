import uk.gov.defra.ffc.Utils

def replaceInFile(from, to, file) {
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

def getFolder(repoName) {
  return Utils.getFolder(repoName)
}
