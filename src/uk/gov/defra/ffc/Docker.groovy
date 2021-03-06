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

  static def pushContainerImage(ctx, imageName) {
    ctx.withCredentials([
      ctx.usernamePassword(credentialsId : ctx.DOCKERHUB_CREDENTIALS_ID,
        usernameVariable: 'username', passwordVariable: 'password')
    ]) {
      ctx.sh("docker login --username ${ctx.username} --password ${ctx.password}")
      ctx.sh("docker push $imageName")
    }
  }

  static String getImageName(String repoName, String tag, String tagSuffix = null, String registry = null) {
    registry = getRegistry(registry)
    tag = getTag(tag, tagSuffix)
    return "${registry}/${repoName}:${tag}"
  }

  static String getRegistry(String registry) {
    return registry != null ? registry : 'defradigital'
  }

  static String getTag(String tag, String tagSuffix = null) {
    return tagSuffix != null ? "${tag}-${tagSuffix}" : tag
  }

  static Boolean containerTagExists(def ctx, String imageName) {
    String[] repositoryMap = createRepositoryMap(imageName)
    String[] existingTags = getImageTags(ctx, repositoryMap[0])
    if (existingTags.contains(repositoryMap[1])) {
      ctx.echo 'Tag exists in repository'
      return true
    }
    return false
  }

  static String createRepositoryMap(String imageName) {
    return imageName.split(':')
  }

  static String[] getImageTags(def ctx, String image) {
    return ctx.sh(script: "curl https://index.docker.io/v1/repositories/$image/tags", returnStdout: true)
  }
}
