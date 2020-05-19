package uk.gov.defra.ffc

import uk.gov.defra.ffc.Utils

class Tests implements Serializable {
  static def runTests(ctx, projectName, serviceName, buildNumber, identityTag) {
    try {
      ctx.sh('mkdir -p test-output')
      ctx.sh('chmod 777 test-output')
      ctx.sh("docker-compose -p $projectName-$identityTag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml run $serviceName")
    } finally {
      ctx.sh("docker-compose -p $projectName-$identityTag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml down -v")
    }
  }
}
