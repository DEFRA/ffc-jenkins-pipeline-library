package uk.gov.defra.ffc

import uk.gov.defra.ffc.Provision

class Docker implements Serializable {
  static def buildTestImage(ctx, credentialsId, registry, projectName, buildNumber, tag) {
   ctx.docker.withRegistry("https://$registry", credentialsId) {
    ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml build")
   }
  }

  static def buildAndPushContainerImage(ctx, credentialsId, registry, imageName, tag, packageManager) {
    ctx.docker.withRegistry("https://$registry", credentialsId) {
      ctx.withCredentials([
        ctx.string(credentialsId: 'artifactory-npm-token', variable: 'npmToken')
       // ctx.string(credentialsId: 'artifactory-nuget-token', variable: 'nugetToken')
      ]) {
        def resourcePath = 'uk/gov/defra/ffc/artifactory'

        if (packageManager == 'npm') {
          Provision.getResourceFile(ctx, resourcePath, '.npmrc', '.')
          ctx.sh("docker-compose -f docker-compose.yaml build --build-arg NPM_REGISTRY=${ctx.NPM_REGISTRY} --build-arg NPM_TOKEN=${ctx.npmToken} --build-arg NPM_EMAIL=${ctx.NPM_EMAIL}")
        } else {
          // TODO do same for .NET
          ctx.sh("docker-compose -f docker-compose.yaml build --build-arg NUGET_REPOSITORY=${ctx.NUGET_REPOSITORY}")
        }
        ctx.sh("docker tag $imageName $registry/$imageName:$tag")
        ctx.sh("docker push $registry/$imageName:$tag")
      }
    }
  }


}
