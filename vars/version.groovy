import uk.gov.defra.ffc.Version

String getCSProjVersion(String projName) {
  return Version.getCSProjVersion(this, projName)
}

String getPackageJsonVersion() {
  return Version.getPackageJsonVersion(this)
}

String getFileVersion(String fileName) {
  return Version.getFileVersion(this, fileName)
}

void verifyCSProjIncremented(String projectName, String defaultBranch) {
  Version.verifyCSProjIncremented(this, projectName, defaultBranch)
}

void verifyPackageJsonIncremented(String defaultBranch) {
  Version.verifyPackageJsonIncremented(this, defaultBranch)
}

void verifyFileIncremented(String fileName) {
  Version.verifyFileIncremented(this, fileName)
}
