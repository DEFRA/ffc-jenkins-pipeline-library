package uk.gov.defra.ffc

class GitHubStatus implements Serializable {
  private static final BASECTX = 'jenkins/ci/'

  class Build {
    static final Context = "${BASECTX}build"
  }

  class BuildTestImage {
    static final Context = "${BASECTX}build-test-image"
    static final Description = 'Build test image'
  }

  class CodeAnalysis {
    static final Context = "${BASECTX}code-analysis"
    static final Description = 'Code analysis'
  }

  class DeployChart {
    static final Context = "${BASECTX}deploy-chart"
    static final Description = 'Deploy chart'
  }

  class HelmLint {
    static final Context = "${BASECTX}helm-lint"
    static final Description = 'Lint Helm chart'
  }

  class NpmAudit {
    static final Context = "${BASECTX}npm-audit"
    static final Description = 'npm audit'
  }

  class RunTests {
    static final Context = "${BASECTX}run-tests"
    static final Description = 'Run tests'
  }

  class RunAcceptanceTests {
    static final Context = "${BASECTX}run-acceptance-tests"
    static final Description = 'Run acceptance tests'
  }

  class SnykTest {
    static final Context = "${BASECTX}snyk-test"
    static final Description = 'Snyk test'
  }

  class VerifyVersion {
    static final Context = "${BASECTX}verify-version"
    static final Description = 'Verify version incremented'
  }

  class ZapScan {
    static final Context = "${BASECTX}zap-version"
    static final Description = 'Zap scan'
  }
}
