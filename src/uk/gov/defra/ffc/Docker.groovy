package uk.gov.defra.ffc

import uk.gov.defra.ffc.Utils

class Docker implements Serializable {
  static def buildTestImage(ctx, credentialsId, registry, projectName, buildNumber, tag) {
    ctx.docker.withRegistry("https://$registry", credentialsId) {
      ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml build")
    }
  }

  static def buildAndPushContainerImage(ctx, credentialsId, registry, imageName, tag) {
    ctx.docker.withRegistry("https://$registry", credentialsId) {
      ctx.sh('docker-compose -f docker-compose.yaml build')
      ctx.sh("docker tag $imageName $registry/$imageName:$tag")
      ctx.sh("docker push $registry/$imageName:$tag")
    }
  }
}
