@Library("endor-shared-lib") _
import com.endorlabs.dockerScan
import com.endorlabs.syncOrg
import com.endorlabs.checkout
def dockerScan = new dockerScan()
def syncOrg = new syncOrg()

pipeline {
  agent {
    label params.AGENT_LABEL
  }
  options { 
    timestamps () 
    buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '30', numToKeepStr: '10'))
    skipDefaultCheckout()
  }
  environment {
    GITHUB_TOKEN = credentials('GITHUB_TOKEN')
    ENDOR_LABS_API_KEY = credentials('ENDOR_LABS_API_KEY')
    ENDOR_LABS_API_SECRET = credentials('ENDOR_LABS_API_SECRET')
  }
  parameters {
    string(
      name: 'AGENT_LABEL',
      description: 'Label of the Jenkins Agents to be used for this job',
      defaultValue: 'gcp-standard',
      trim: true
    )
    string(
      name: 'GITHUB_ORG',
      description: 'Github Org',
      defaultValue: 'endorlabstest',
      trim: true
    )
    string(
      name: 'ENDORCTL_VERSION',
      description: 'Endor Version',
      defaultValue: 'latest',
      trim: true
    )
    string(
      name: 'ENDOR_LABS_API',
      description: 'Sets the API URL for the Endor Labs Application',
      defaultValue: 'https://api.staging.endorlabs.com',
      trim: true
    )
    string(
      name: 'ENDOR_LABS_NAMESPACE',
      description: 'Sets the namespace used for namespace-scoped resources',
      defaultValue: 'jenkins',
        trim: true
    )
    string(
      name: 'SCAN_SUMMARY_OUTPUT_TYPE',
      description: 'Set to the desired output format. Supported formats: json | yaml | table | summary. (default "table")',
      defaultValue: 'table',
      trim: true
    )
    booleanParam(
      name: 'CI_RUN',
      description: 'Signifies that this is a CI run of endorctl',
      defaultValue: false
    )
    string(
      name: 'CI_RUN_TAGS',
      description: 'Sets the tags for a CI run',
      defaultValue: '',
      trim: true
    )
    string(
      name: 'LOG_LEVEL',
      description: 'Sets the log level of the application. (default "info")',
      defaultValue: 'info',
      trim: true
    )
    booleanParam(
      name: 'LOG_VERBOSE',
      description: 'Makes the log verbose',
      defaultValue: true
    )
    string(
      name: 'LANGUAGES',
      description: 'Set programming languages to scan. Supported languages: c#, go, java, javascript, php, python, ruby, rust, scala, typescript (default [c#,go,java,javascript,php,python,ruby,rust,scala,typescript])',
      defaultValue: '',
      trim: true
    )
    string(
      name: 'ADDITIONAL_ARGS',
      description: 'Additional Arguments.',
      defaultValue: '',
      trim: true
    )
    string(
      name: 'NO_OF_THREADS',
      description: 'No of parallel threads for scanning',
      defaultValue: '9',
      trim: true
    )
  }
  stages {
    stage('Docker Pull') {
      steps {
        script {
          util.pullEndorDockerImage(params.ENDORCTL_VERSION)
        }
      }
    }
    stage('Sync Org') {
      steps {
        script {
          syncOrg.execute(this)
          def projectCount = syncOrg.projectCount(this)
          echo "Project Count: ${projectCount}"
          env.PROJECT_COUNT = projectCount
        }
      }
    }
    stage("Trigger Parallel Scan") {
      steps {
        script {
          def projects = []
          syncOrg.getProjectList(projects, this)
          def parallellExecutor = params.NO_OF_THREADS.toInteger()
          def repository_group = projects.collate parallellExecutor
          for (def repos : repository_group) {
            def targets = [:]
            for (String project : repos) {
              generate_scan_stages(targets, project, params.AGENT_LABEL)
            }
            parallel targets
          }
        }
      }
    }
  }
}

def generate_scan_stages(def targets, def project, def agent_label) {
  targets[project] = {
    def dockerScan = new dockerScan()
    def checkout = new checkout()
    String projectName = projectName(project)
    String stageName = "Scan " + projectName
    node(agent_label) {
      stage("Scan $projectName") {
        try {
          checkout.https(this, project)
          dockerScan.execute(this)
        } catch (err) {
          unstable("endorctl Scan failed for ${project}")
        }
      }
    }
  }
}

def projectName(String project) {
  def matcher = project =~ /^https:\/\/([a-zA-Z\-\.]+)\/(?<proj>[a-zA-Z\-_]+\/[a-zA-Z\-_]+)\.git$/
  if (matcher.matches()) {
      return matcher.group("proj")
  } else {
      return project
  }
}
