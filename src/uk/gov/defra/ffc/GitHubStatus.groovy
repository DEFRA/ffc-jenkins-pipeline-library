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

  class GitHubSuperLinter {
    static final Context = "${BASECTX}github-super-linter"
    static final Description = 'Run GitHub Super-Linter'
  }

  class HelmLint {
    static final Context = "${BASECTX}helm-lint"
    static final Description = 'Lint Helm chart'
  }

  class NpmAudit {
    static final Context = "${BASECTX}npm-audit"
    static final Description = 'npm audit'
  }

  class RunAcceptanceTests {
    static final Context = "${BASECTX}run-acceptance-tests"
    static final Description = 'Run acceptance tests'
  }

  class RunTests {
    static final Context = "${BASECTX}run-tests"
    static final Description = 'Run tests'
  }

  class SnykTest {
    static final Context = "${BASECTX}snyk-test"
    static final Description = 'Snyk test'
  }

  class PactBrokerTest {
    static final Context = "${BASECTX}pact-broker-test"
    static final Description = 'PactBroker test'
  }

  class VerifyVersion {
    static final Context = "${BASECTX}verify-version"
    static final Description = 'Verify version incremented'
  }

  class ZapScan {
    static final Context = "${BASECTX}zap-version"
    static final Description = 'Zap scan'
  }

  class Pa11y {
    static final Context = "${BASECTX}pa11y-version"
    static final Description = 'Accessibility tests'
  }
}
