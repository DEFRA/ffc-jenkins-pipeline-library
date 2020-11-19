import uk.gov.defra.ffc.Version

def getCSProjVersion(String projName) {
  return Version.getCSProjVersion(this, projName)
}

def getPackageJsonVersion() {
  return Version.getPackageJsonVersion(this)
}

def getFileVersion(String fileName) {
  return Version.getFileVersion(this, fileName)
}

def verifyCSProjIncremented(String projectName, String defaultBranch) {
  Version.verifyCSProjIncremented(this, projectName, defaultBranch)
}

def verifyPackageJsonIncremented(String defaultBranch) {
  Version.verifyPackageJsonIncremented(this, defaultBranch)
}

def verifyFileIncremented(String fileName) {
  Version.verifyFileIncremented(this, fileName)
}
