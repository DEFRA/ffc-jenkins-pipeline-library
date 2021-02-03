package uk.gov.defra.ffc

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

  static def buildContainerImage(ctx, imageName) {
    ctx.sh("docker build --no-cache --tag ${imageName} .")
  }

  static String getImageName(String repoName, String tag, String tagSuffix = null, String registry = null) {
    registry = getRegistry(registry)
    tag = getTag(tag, tagSuffix)
    return "${registry}/${repoName}:${tag}"
  }

  static String getRegistry(String registry) {
    return registry != null ? registry : 'defra-digital'
  }

  static String getTag(String tag, String tagSuffix) {
    return tagSuffix != null ? "${tag}-${tagSuffix}" : tag
  }
}
