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

def verifyCSProjIncremented(projectName) {
  Version.verifyCSProjIncremented(this, projectName)
}

def verifyPackageJsonIncremented() {
  Version.verifyPackageJsonIncremented(this)
}

def verifyFileIncremented(fileName) {
  Version.verifyFileIncremented(this, fileName)
}
