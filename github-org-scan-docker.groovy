@Library("endor-shared-lib") _
import com.endorlabs.dockerScan
import com.endorlabs.syncOrg
import com.endorlabs.checkout
def dockerScan = new dockerScan()
def syncOrg = new syncOrg()
def parameters = [:]
getParameters(parameters)

pipeline {
  agent {
    label parameters['AGENT_LABEL']
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
      name: 'ENDORCTL_VERSION',
      description: 'Endor Version',
      defaultValue: 'latest',
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
          util.pullEndorDockerImage(parameters['ENDORCTL_VERSION'])
        }
      }
    }
    stage('Sync Org') {
      steps {
        script {
          syncOrg.execute(this, parameters)
          def projectCount = syncOrg.getProjectCount(this, parameters)
          echo "Project Count: ${projectCount}"
        }
      }
    }
    stage("Trigger Parallel Scan") {
      steps {
        script {
          def projects = []
          syncOrg.getProjectList(projects, this, parameters)
          def parallellExecutor = parameters['NO_OF_THREADS'].toInteger()
          def repository_group = projects.collate parallellExecutor
          for (def repos : repository_group) {
            def targets = [:]
            for (String project : repos) {
              generate_scan_stages(targets, project, parameters['AGENT_LABEL'])
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
          checkout.setCredentialHelper(this)
          def branch = checkout.getDefaultBranch(this, parameters)
          checkout.https(this, project, branch)
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

def getParameters(def parameters) {
  if (params.AGENT_LABEL) {
    parameters['AGENT_LABEL'] = params.AGENT_LABEL
  } else if (env.AGENT_LABEL) {
    parameters['AGENT_LABEL'] = env.AGENT_LABEL
  } else {
    error "ERROR: Agent Label is Mandatory. It should made available as AGENT_LABEL"
  }
  if (params.ENDORCTL_VERSION) {
    parameters['ENDORCTL_VERSION'] = params.ENDORCTL_VERSION
  } else if (env.ENDORCTL_VERSION) {
    parameters['ENDORCTL_VERSION'] = env.ENDORCTL_VERSION
  } else {
    parameters['ENDORCTL_VERSION'] = 'latest'
  }
  if (params.ENDOR_LABS_NAMESPACE) {
    parameters['ENDOR_LABS_NAMESPACE'] = params.ENDOR_LABS_NAMESPACE
  } else if (env.ENDOR_LABS_NAMESPACE) {
    parameters['ENDOR_LABS_NAMESPACE'] = env.ENDOR_LABS_NAMESPACE
  } else {
    error "ERROR: Tenant Name or Namespace should be made available as ENDOR_LABS_NAMESPACE"
  }
  if (params.SCAN_SUMMARY_OUTPUT_TYPE) {
    parameters['SCAN_SUMMARY_OUTPUT_TYPE'] = params.SCAN_SUMMARY_OUTPUT_TYPE
  } else if (env.SCAN_SUMMARY_OUTPUT_TYPE) {
    parameters['SCAN_SUMMARY_OUTPUT_TYPE'] = env.SCAN_SUMMARY_OUTPUT_TYPE
  } else {
    parameters['SCAN_SUMMARY_OUTPUT_TYPE'] = 'table'
  }
  if (params.LOG_LEVEL) {
    parameters['LOG_LEVEL'] = params.LOG_LEVEL
  } else if (env.LOG_LEVEL) {
    parameters['LOG_LEVEL'] = env.LOG_LEVEL
  } else {
    parameters['LOG_LEVEL'] = 'info'
  }
  if (params.LOG_VERBOSE) {
    parameters['LOG_VERBOSE'] = params.LOG_VERBOSE
  } else if (env.LOG_VERBOSE) {
    parameters['LOG_VERBOSE'] = env.LOG_VERBOSE
  } else {
    parameters['LOG_VERBOSE'] = false
  }
  if (params.LANGUAGES) {
    parameters['LANGUAGES'] = params.LANGUAGES
  } else if (env.LANGUAGES) {
    parameters['LANGUAGES'] = env.LANGUAGES
  } else {
    parameters['LANGUAGES'] = ''
  }
  if (params.ADDITIONAL_ARGS) {
    parameters['ADDITIONAL_ARGS'] = params.ADDITIONAL_ARGS
  } else if (env.ADDITIONAL_ARGS) {
    parameters['ADDITIONAL_ARGS'] = env.ADDITIONAL_ARGS
  } else {
    parameters['ADDITIONAL_ARGS'] = ''
  }
  if (params.NO_OF_THREADS) {
    parameters['NO_OF_THREADS'] = params.NO_OF_THREADS
  } else if (env.NO_OF_THREADS) {
    parameters['NO_OF_THREADS'] = env.NO_OF_THREADS
  } else {
    parameters['NO_OF_THREADS'] = 5
  }
  if (env.GITHUB_TOKEN) {
    parameters['GITHUB_TOKEN'] = env.GITHUB_TOKEN
  } else {
    error "ERROR: Github Access Token should be made available as GITHUB_TOKEN"
  }
  if (env.ENDOR_LABS_API_KEY) {
    parameters['ENDOR_LABS_API_KEY'] = env.ENDOR_LABS_API_KEY
  } else {
    error "ERROR: Endor Labs API Key is required and should be made available as ENDOR_LABS_API_KEY"
  }
  if (env.ENDOR_LABS_API_SECRET) {
    parameters['ENDOR_LABS_API_SECRET'] = env.ENDOR_LABS_API_SECRET
  } else {
    error "ERROR: Endor Labs API Secret is required and should be made available as ENDOR_LABS_API_SECRET"
  }
}