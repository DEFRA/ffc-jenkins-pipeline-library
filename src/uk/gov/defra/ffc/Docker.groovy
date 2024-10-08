package uk.gov.defra.ffc
import uk.gov.defra.ffc.Utils

class Docker implements Serializable {

  static def runNodeTestImage(ctx, nodeTestImage, repoName) {
    ctx.sh('mkdir -p -m 777 test-output')
    ctx.sh("docker run --rm -i -v \$(pwd)/jest.setup.js:/home/node/jest.setup.js -v \$(pwd)/jest.config.js:/home/node/jest.config.js -v \$(pwd)/__mocks__:/home/node/__mocks__ -v \$(pwd)/test-output:/home/node/test-output -v \$(pwd)/jest:/home/node/jest -v \$(pwd)/app:/home/node/app -v \$(pwd)/src:/home/node/src -v \$(pwd)/${repoName}:/home/node/${repoName} -v \$(pwd)/test:/home/node/test -v \$(pwd)/package.json:/home/node/package.json ${nodeTestImage} /bin/sh -c 'npm install; npm run test'")
  }

  static def buildTestImage(ctx, credentialsId, registry, projectName, buildNumber, tag) {
   ctx.docker.withRegistry("https://$registry", credentialsId) {
    String sanitizedTag = Utils.sanitizeTag(tag)
    ctx.sh("docker-compose -p $projectName-${sanitizedTag}-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml build")
   }
  }

  static def buildAndPushContainerImage(ctx, credentialsId, registry, imageName, tag) {
    ctx.docker.withRegistry("https://$registry", credentialsId) {
      ctx.sh('docker-compose -f docker-compose.yaml build')
      ctx.sh("docker tag $imageName $registry/$imageName:$tag")
      ctx.sh("docker push $registry/$imageName:$tag")
    }
  }

  static def deleteContainerImage(ctx, imageName, tag) {
    ctx.docker.withRegistry("https://${ctx.DOCKER_REGISTRY}", ctx.DOCKER_REGISTRY_CREDENTIALS_ID) {
      ctx.sh("az acr repository untag --name ${ctx.DOCKER_REGISTRY} --image $imageName:$tag --yes || echo error untagging image $imageName:$tag")
    }
  }

  static def deleteDanglingImages(ctx) {
    ctx.docker.withRegistry("https://${ctx.DOCKER_REGISTRY}", ctx.DOCKER_REGISTRY_CREDENTIALS_ID) {
      def repositories = listRepositories(ctx)
      repositories.each {
        ctx.sh("az acr repository show-manifests --name ${ctx.DOCKER_REGISTRY} --repository ${it} --query '[?tags[0]==null].digest' -o tsv | xargs -I% az acr repository delete --name ${ctx.DOCKER_REGISTRY} --image ${it}@% --y")
      }
    }
  }

  static def listRepositories(ctx) {
    ctx.docker.withRegistry("https://${ctx.DOCKER_REGISTRY}", ctx.DOCKER_REGISTRY_CREDENTIALS_ID) {
      def script = "az acr repository list --name ${ctx.DOCKER_REGISTRY} -o tsv"
      def repositories = ctx.sh(returnStdout: true, script: script).trim()
      return repositories.tokenize('\n')
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
