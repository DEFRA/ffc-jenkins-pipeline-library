// public
def getCSProjVersion(projName) {
  return sh(returnStdout: true, script: "xmllint ${projName}/${projName}.csproj --xpath '//Project/PropertyGroup/Version/text()'").trim()
}

// private
def getCSProjVersionMaster(projName) {
  return sh(returnStdout: true, script: "git show origin/master:${projName}/${projName}.csproj | xmllint --xpath '//Project/PropertyGroup/Version/text()' -").trim()
}

// public
def getPackageJsonVersion() {
  return sh(returnStdout: true, script: "jq -r '.version' package.json").trim()
}

// private
def getPackageJsonVersionMaster() {
  return sh(returnStdout: true, script: "git show origin/master:package.json | jq -r '.version'").trim()
}

// public
def getFileVersion(fileName) {
  return sh(returnStdout: true, script: "cat ${fileName}").trim()
}

// private
def getFileVersionMaster(fileName) {
  return sh(returnStdout: true, script: "git show origin/master:${fileName}").trim()
}

// public
def verifyCSProjIncremented(projectName) {
  def masterVersion = getCSProjVersionMaster(projectName)
  def version = getCSProjVersion(projectName)
  errorOnNoVersionIncrement(masterVersion, version)
}

// public
def verifyPackageJsonIncremented() {
  def masterVersion = getPackageJsonVersionMaster()
  def version = getPackageJsonVersion()
  errorOnNoVersionIncrement(masterVersion, version)
}

// public
def verifyFileIncremented(fileName) {
  def masterVersion = getFileVersionMaster(fileName)
  def version = getFileVersion(fileName)
  errorOnNoVersionIncrement(masterVersion, version)
}

// private
def errorOnNoVersionIncrement(masterVersion, version){
  def cleanMasterVersion = extractSemVerVersion(masterVersion)
  def cleanVersion = extractSemVerVersion(version)
  if (hasIncremented(cleanMasterVersion, cleanVersion)) {
    echo "version increment valid '$masterVersion' -> '$version'"
  } else {
    error( "version increment invalid '$masterVersion' -> '$version'")
  }
}

// private
def extractSemVerVersion(versionTag) {
  def splitTag = versionTag.split(/^v-/)
  return splitTag.length > 1 ? splitTag[1] : versionTag
}

// private
def hasIncremented(currVers, newVers) {
  // For a newly created empty repository currVers will be empty on first merge to master
  // consider 'newVers' the first version and return true
  if (currVers == '') {
    return true
  }
  try {
    currVersList = currVers.tokenize('.').collect { it.toInteger() }
    newVersList = newVers.tokenize('.').collect { it.toInteger() }
    return currVersList.size() == 3 &&
           newVersList.size() == 3 &&
           [0, 1, 2].any { newVersList[it] > currVersList[it] }
  }
  catch (Exception ex) {
    return false
  }
}
