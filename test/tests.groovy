GroovyShell shell = new GroovyShell()
def provision = shell.parse(new File('./vars/provision.groovy'))

provision.validateQueueName('mhtest?')
println "lol"
