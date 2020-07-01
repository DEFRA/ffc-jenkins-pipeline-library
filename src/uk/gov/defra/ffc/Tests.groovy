package uk.gov.defra.ffc

import uk.gov.defra.ffc.Helm
import uk.gov.defra.ffc.Utils

class Tests implements Serializable {
  static def runTests(ctx, projectName, serviceName, buildNumber, tag) {
    try {
      ctx.sh('mkdir -p test-output')
      ctx.sh('chmod 777 test-output')
      ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml run $serviceName")
    } finally {
      ctx.sh("docker-compose -p $projectName-$tag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml down -v")
    }
  }

  static def lintHelm(ctx, chartName) {
    Helm.addHelmRepo(ctx, 'ffc-public', ctx.HELM_CHART_REPO_PUBLIC)
    ctx.sh("helm dependency update ./helm/$chartName")
    ctx.sh("helm lint ./helm/$chartName")
  }

  static def createJUnitReport(ctx) {
    ctx.junit('test-output/junit.xml')
  }

  static def deleteOutput(ctx, containerImage, containerWorkDir) {
    // clean up files created by node/ubuntu user that cannot be deleted by jenkins. Note: uses global environment variable
    ctx.sh("[ -d \"$ctx.WORKSPACE/test-output\" ] && docker run --rm -u node --mount type=bind,source='$ctx.WORKSPACE/test-output',target=/$containerWorkDir/test-output $containerImage rm -rf test-output/*")
  }

  static def analyseCode(ctx, sonarQubeEnv, sonarScanner, params) {
    def scannerHome = ctx.tool sonarScanner
    ctx.withSonarQubeEnv(sonarQubeEnv) {
      def args = ''
      params.each { param ->
        args = args + " -D$param.key=$param.value"
      }

      ctx.sh("$scannerHome/bin/sonar-scanner$args")
    }
  }

  static def analyseDotNetCode(ctx, project, params) {
    ctx.withCredentials([
      ctx.string(credentialsId: 'sonarcloud-token', variable: 'token'),
    ]) {
      def args = ''
      params.each { param ->
        args = args + " -e $param.key=$param.value"
      }
      ctx.("chmod 777 $(pwd)")
      ctx.sh("docker run -v \$(pwd)/$project/:/home/dotnet/project -e SONAR_TOKEN=$ctx.token $args defradigital/ffc-dotnet-core-sonar")
    }
  }

  static def createSonarDirectory(ctx) {
    ctx.sh("mkdir -p \$(pwd)/.sonarqube")
    ctx.sh("chmod 777 \$(pwd)/.sonarqube")
  }

  static def buildCodeAnalysisDefaultParams(projectName, branch, pr) {    
    def params = [
    'sonar.organization': 'defra',
    'sonar.projectKey': projectName,
    'sonar.sources': '.'
    ];

    if(pr != '') {
      params = params + buildCodeAnalysisPRParams(projectName, branch, pr)
    }

    return params
  }

  static def buildCodeAnalysisPRParams(projectName, branch, pr) {
    return [
    'sonar.pullrequest.base': 'master',
    'sonar.pullrequest.branch': branch,
    'sonar.pullrequest.key': pr,
    'sonar.pullrequest.provider': 'GitHub',
    'sonar.pullrequest.github.repository': "defra/${projectName}"
    ];
  }

  static def buildCodeAnalysisDotNetParams(projectName, branch, pr) {    
    def params = [
    'SONAR_ORGANIZATION': 'defra',
    'SONAR_PROJECT_KEY': projectName
    ];

    if(pr != '') {
      params = params + buildCodeAnalysisDotNetPRParams(projectName, branch, pr)
    }

    return params
  }

  static def buildCodeAnalysisDotNetPRParams(projectName, branch, pr) {
    return [
    'SONAR_PR_BASE': 'master',
    'SONAR_PR_BRANCH': branch,
    'SONAR_PR_KEY': pr,
    'SONAR_PR_PROVIDER': 'GitHub',
    'SONAR_PR_REPOSITORY': "defra/${projectName}"
    ];
  }  
}
