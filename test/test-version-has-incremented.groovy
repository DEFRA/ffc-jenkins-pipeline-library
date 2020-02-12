GroovyShell shell = new GroovyShell()
def defraUtils = shell.parse(new File('./src/uk/gov/defra/ffc/DefraUtils.groovy'))

assert defraUtils.versionHasIncremented('1.0.0', '2.0.0') == true
assert defraUtils.versionHasIncremented('1.0.0', '1.1.0') == true
assert defraUtils.versionHasIncremented('1.0.0', '1.0.1') == true
assert defraUtils.versionHasIncremented('2.0.0', '1.0.0') == false
assert defraUtils.versionHasIncremented('1.1.0', '1.0.0') == false
assert defraUtils.versionHasIncremented('1.0.1', '1.0.0') == false
assert defraUtils.versionHasIncremented('1.0.0', '1.0.0') == false
assert defraUtils.versionHasIncremented('1x.0.0', '2.0.0') == false
assert defraUtils.versionHasIncremented('1.0.0.0', '2.0.0') == false
assert defraUtils.versionHasIncremented('1.0.0', '2.0.0.0') == false
