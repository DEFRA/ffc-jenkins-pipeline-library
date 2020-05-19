package uk.gov.defra.ffc

import uk.gov.defra.ffc.Utils

class Docker implements Serializable {
  static def buildTestImage(ctx, credentialsId, registry, projectName, buildNumber, identityTag) {
    ctx.docker.withRegistry("https://$registry", credentialsId) {
      ctx.sh("docker-compose -p $projectName-$identityTag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml build --no-cache")
    }
  }

  static def buildAndPushContainerImage(ctx, credentialsId, registry, imageName, tag) {
    ctx.docker.withRegistry("https://$registry", credentialsId) {
      ctx.sh('docker-compose -f docker-compose.yaml build --no-cache')
      ctx.sh("docker tag $imageName $registry/$imageName:$tag")
      ctx.sh("docker push $registry/$imageName:$tag")
    }
  }
}
