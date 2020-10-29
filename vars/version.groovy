import uk.gov.defra.ffc.Version

def getCSProjVersion(projName) {
  return Version.getCSProjVersion(this, projName)
}

def getPackageJsonVersion() {
  return Version.getPackageJsonVersion(this)
}

def getFileVersion(fileName) {
  return Version.getFileVersion(this, fileName)
}

def verifyCSProjIncremented(projectName, defaultBranch) {
  Version.verifyCSProjIncremented(this, projectName, defaultBranch)
}

def verifyPackageJsonIncremented(defaultBranch) {
  Version.verifyPackageJsonIncremented(this, defaultBranch)
}

def verifyFileIncremented(fileName) {
  Version.verifyFileIncremented(this, fileName)
}
