import uk.gov.defra.ffc.Pact

def string(credentialsId,variable){
  Pact.string(this, credentialsId, variable)
}

def usernamePassword(credentialsId, usernameVariable, passwordVariable){
  Pact.usernamePassword(this, credentialsId, usernameVariable, passwordVariable)
}

def publishPacts(repoName, string, usernamePassword){
  Pact.publishPacts(this, repoName, string, usernamePassword)
}

