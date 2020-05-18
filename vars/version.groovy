import uk.gov.defra.ffc.Version

def getCSProjVersion(projName) {
  return sh(returnStdout: true, script: "xmllint $projName/${projName}.csproj --xpath '//Project/PropertyGroup/Version/text()'").trim()
}

def getPackageJsonVersion() {
  return sh(returnStdout: true, script: "jq -r '.version' package.json").trim()
}

def getFileVersion(fileName) {
  return sh(returnStdout: true, script: "cat $fileName").trim()
}

def verifyCSProjIncremented(projectName) {
  def masterVersion = Version.getCSProjVersionMaster(this, projectName)
  def version = getCSProjVersion(projectName)
  Version.errorOnNoVersionIncrement(this, masterVersion, version)
}

def verifyPackageJsonIncremented() {
  def masterVersion = Version.getPackageJsonVersionMaster(this)
  def version = getPackageJsonVersion()
  Version.errorOnNoVersionIncrement(this, masterVersion, version)
}

def verifyFileIncremented(fileName) {
  def currentVersion = getFileVersion(fileName)
  def previousVersion = Version.getPreviousFileVersion(this, fileName, currentVersion)
  Version.errorOnNoVersionIncrement(this, previousVersion, currentVersion)
}
