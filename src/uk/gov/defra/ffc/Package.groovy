package uk.gov.defra.ffc

class Package implements Serializable {
  static def publishToNpm(def ctx, String version = '') {
    ctx.withCredentials([
      ctx.string(credentialsId: 'npm-publish-token', variable: 'token')
    ]) {
      if {
        ctx.sh("docker run --rm -v  \$(pwd)/:\\/home\\/node\\/ -e NPM_TOKEN=${ctx.token} -e VERSION=${version} defradigital/ffc-npm-publish")
      }
    }
  }
}
