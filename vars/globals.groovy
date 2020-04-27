import groovy.transform.Field

@Field testVar = "hello from globals"

def runMe() {
  echo "IN GLOBALS FUNC"
}