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
