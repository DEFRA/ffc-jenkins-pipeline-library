package uk.gov.defra.ffc

class Docker implements Serializable {
  static def buildTestImage(ctx, credentialsId, registry, projectName, buildNumber, tag) {
   ctx.docker.withRegistry("https://$registry", credentialsId) {
    ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml build")
   }
  }

  static def buildAndPushContainerImage(ctx, credentialsId, registry, imageName, tag, packageManager) {
    ctx.docker.withRegistry("https://$registry", credentialsId) {
      if (packageManager == 'NPM_REGISTRY') {
        ctx.sh("docker-compose -f docker-compose.yaml build --build-arg NPM_REGISTRY=${ctx.NPM_REGISTRY}")
      } else {
        ctx.sh("docker-compose -f docker-compose.yaml build --build-arg NUGET_REPOSITORY=${ctx.NUGET_REPOSITORY}")
      }
      ctx.sh("docker tag $imageName $registry/$imageName:$tag")
      ctx.sh("docker push $registry/$imageName:$tag")
    }
  }
}
