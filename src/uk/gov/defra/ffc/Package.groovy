package uk.gov.defra.ffc

class Package implements Serializable {
  static def publishToNpm(def ctx, Boolean tagNext = false) {
    ctx.withCredentials([
      ctx.string(credentialsId: 'npm-publish-token', variable: 'token')
    ]) {  
      ctx.sh("docker run --rm -v  \$(pwd)/:\\/home\\/node\\/ -e NPM_TOKEN=${ctx.token} -e TAG_NEXT=${tagNext} defradigital/ffc-npm-publish")
    }
  }
}
