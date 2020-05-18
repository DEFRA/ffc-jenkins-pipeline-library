import uk.gov.defra.ffc.Utils

// public
def replaceInFile(from, to, file) {
  Utils.replaceInFile(this, from, to, file)
  // sh "sed -i -e 's/$from/$to/g' $file"
}

// public
def getCommitMessage() {
  return Utils.getCommitMessage(this)
  // return sh(returnStdout: true, script: 'git log -1 --pretty=%B | cat')
}

// public
def getCommitSha() {
  return Utils.getCommitSha(this)
  // return sh(returnStdout: true, script: "git rev-parse HEAD").trim()
}
