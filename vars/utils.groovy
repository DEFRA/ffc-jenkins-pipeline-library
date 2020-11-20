import uk.gov.defra.ffc.Utils

void replaceInFile(String from, String to, String file) {
  Utils.replaceInFile(this, from, to, file)
}

String getCommitMessage() {
  return Utils.getCommitMessage(this)
}

String getCommitSha() {
  return Utils.getCommitSha(this)
}

String getRepoName() {
  return Utils.getRepoName(this)
}

String getErrorMessage(e) {
  return Utils.getErrorMessage(e)
}
