package uk.gov.defra.ffc

class Package implements Serializable {
  static def publishToNpm(def ctx, Boolean tagNext = false) {
    ctx.withCredentials([
      ctx.string(credentialsId: 'npm-publish-token', variable: 'token')
    ]) {  
      ctx.sh("docker run --rm -v '${ctx.PWD}:/src' -e NPM_TOKEN=${ctx.token} -e TAG_NEXT=${tagNext} tozny/npm-publish")
    }
  }
}
