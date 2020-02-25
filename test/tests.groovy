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

def testMapHasKeys(du) {
  def test
  def Map sampleMap;
  sampleMap = [a: 1, b: 2, c: [d: 3, e: 4]];
  def List theseChecksPass;
  theseChecksPass = ['a', 'b', [c: ['d', 'e']]];
  def List theseChecksFail;
  theseChecksFail = ['a', 'b', [c: ['d', 'e']], 'f'];
  def List theseChecksAlsoFail;
  theseChecksAlsoFail = ['a', 'b', [c: ['d', 'e', 'f']]];

  assert du.__hasKeys(sampleMap, theseChecksPass) == true;
  assert du.__hasKeys(sampleMap, theseChecksFail) == false;
  assert du.__hasKeys(sampleMap, theseChecksAlsoFail) == false;
}

def testTerraformInputVariables(du) {
  def Map inputs1;
  inputs1 = [a: 1, b: 2, c: [d: 3, e: 4]];
  def Map inputs2;
  inputs2 = [code: "abc-123", pr_code: 12, foo: [bar: "foo", foo: true]];

  assert du.__generateTerraformInputVariables(inputs1) == "a = 1\nb = 2\nc = {\n\td = 3\n\te = 4\n}"
  assert du.__generateTerraformInputVariables(inputs2) == "code = \"abc-123\"\npr_code = 12\nfoo = {\n\tbar = \"foo\"\n\tfoo = true\n}" // "code = \"abc-123\"\npr_code = 12\nfoo = {\n\tbar = \"foo\"\n\tfoo = true\n}'"
}

def testMapArrayToArgsMapsArraySingleValue(du) {
  assert du.mapArrayToArgs(['--build-arg':'REGISTRY=TestRegistry']) == ' --build-arg REGISTRY=TestRegistry'
}

def testMapArrayToArgsMapsArrayMultipleValues(du) {
  assert du.mapArrayToArgs(['--build-arg':'REGISTRY=TestRegistry',
    '--build-arg':'REGISTRY=TestRegistry2']) == ' --build-arg REGISTRY=TestRegistry --build-arg REGISTRY=TestRegistry2'
}

testVersionHasIncremented(defraUtils)
testMapHasKeys(defraUtils)
testTerraformInputVariables(defraUtils)
testMapArrayToArgsMapsArraySingleValue(defraUtils)
testMapArrayToArgsMapsArrayMultipleValues(defraUtils)
