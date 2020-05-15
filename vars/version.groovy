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
def getPreviousFileVersion(fileName, currentVersion) {
  def majorVersion = currentVersion[0]
  return sh(returnStdout: true, script: "git show \$(git ls-remote origin -t $majorVersion | cut -f 1):${fileName}").trim()
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
  def currentVersion = getFileVersion(fileName)
  def previousVersion = getPreviousFileVersion(fileName, currentVersion)
  errorOnNoVersionIncrement(previousVersion, currentVersion)
}

// private
def errorOnNoVersionIncrement(previousVersion, currentVersion){
  def cleanPreviousVersion = extractSemVerVersion(previousVersion)
  def cleanCurrentVersion = extractSemVerVersion(currentVersion)
  if (hasIncremented(cleanPreviousVersion, cleanCurrentVersion)) {
    echo "version increment valid '$previousVersion' -> '$currentVersion'"
  } else {
    error( "version increment invalid '$previousVersion' -> '$currentVersion'")
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
