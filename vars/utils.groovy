// public
def replaceInFile(from, to, file) {
  sh "sed -i -e 's/$from/$to/g' $file"
}

// public
def getCommitMessage() {
  return sh(returnStdout: true, script: 'git log -1 --pretty=%B | cat')
}

// public
def getCommitSha() {
  return sh(returnStdout: true, script: "git rev-parse HEAD").trim()
}
