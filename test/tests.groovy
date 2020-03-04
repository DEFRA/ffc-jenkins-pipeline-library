GroovyShell shell = new GroovyShell()
def defraUtils = shell.parse(new File('./src/uk/gov/defra/ffc/DefraUtils.groovy'))

def testVersionHasIncremented(du) {
  assert du.versionHasIncremented('1.0.0', '2.0.0') == true
  assert du.versionHasIncremented('1.0.0', '1.1.0') == true
  assert du.versionHasIncremented('1.0.0', '1.0.1') == true
  assert du.versionHasIncremented('2.0.0', '1.0.0') == false
  assert du.versionHasIncremented('1.1.0', '1.0.0') == false
  assert du.versionHasIncremented('1.0.1', '1.0.0') == false
  assert du.versionHasIncremented('1.0.0', '1.0.0') == false
  assert du.versionHasIncremented('1x.0.0', '2.0.0') == false
  assert du.versionHasIncremented('1.0.0', '2x.0.0') == false
  assert du.versionHasIncremented('1.0.0.0', '2.0.0') == false
  assert du.versionHasIncremented('1.0.0', '2.0.0.0') == false
}

def testTerraformInputVariables(du) {
  assert du.generateTerraformInputVariables('AAA', 'desc', 'AAA', 12, 'dole_queue', 'my-repo') ==
    "service = {\n\tcode = \"AAA\"\n\tname = \"desc\"\n\ttype = \"AAA\"\n}\npr_code = 12\nqueue_purpose = \"dole_queue\"\nrepo_name = \"my-repo\""
  assert du.generateTerraformInputVariables('FFC', 'descdescdesc', 'CCF', 22, 'post_office', 'repo-B') ==
    "service = {\n\tcode = \"FFC\"\n\tname = \"descdescdesc\"\n\ttype = \"CCF\"\n}\npr_code = 22\nqueue_purpose = \"post_office\"\nrepo_name = \"repo-B\""
  assert du.generateTerraformInputVariables('GGT', 'Descy McDescFace', 'TTZ', 1091, 'very_long', 'rep-DD89') ==
    "service = {\n\tcode = \"GGT\"\n\tname = \"Descy McDescFace\"\n\ttype = \"TTZ\"\n}\npr_code = 1091\nqueue_purpose = \"very_long\"\nrepo_name = \"rep-DD89\""
}

testVersionHasIncremented(defraUtils)
testTerraformInputVariables(defraUtils)
