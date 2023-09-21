@Library("endor-shared-lib") _
import com.endorlabs.dockerScan
import com.endorlabs.syncOrg
import com.endorlabs.checkout
def dockerScan = new dockerScan()
def syncOrg = new syncOrg()
def args = [:]
getParameters(args)

pipeline {
  agent {
    label args['AGENT_LABEL']
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
  stages {
    stage('Docker Pull') {
      steps {
        script {
          util.pullEndorDockerImage(args['ENDORCTL_VERSION'])
        }
      }
    }
    stage('Sync Org') {
      steps {
        script {
          syncOrg.execute(this, args)
          def projectCount = syncOrg.getProjectCount(this, args)
          echo "Project Count: ${projectCount}"
        }
      }
    }
    stage("Trigger Parallel Scan") {
      steps {
        script {
          def projects = []
          syncOrg.getProjectList(projects, this, args)
          def parallellExecutor = args['NO_OF_THREADS'].toInteger()
          def repository_group = projects.collate parallellExecutor
          for (def repos : repository_group) {
            def targets = [:]
            for (String project : repos) {
              generate_scan_stages(targets, project, args['AGENT_LABEL'])
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
          def branch = checkout.getDefaultBranch(this, args)
          checkout.https(this, project, branch)
          dockerScan.execute(this, branch)
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

def getParameters(def args) {
  if (params.AGENT_LABEL) {
    args['AGENT_LABEL'] = params.AGENT_LABEL
  } else if (env.AGENT_LABEL) {
    args['AGENT_LABEL'] = env.AGENT_LABEL
  } else {
    error "ERROR: Agent Label is Mandatory. It should made available as AGENT_LABEL"
  }
  if (params.ENDORCTL_VERSION) {
    args['ENDORCTL_VERSION'] = params.ENDORCTL_VERSION
  } else if (env.ENDORCTL_VERSION) {
    args['ENDORCTL_VERSION'] = env.ENDORCTL_VERSION
  } else {
    args['ENDORCTL_VERSION'] = 'latest'
  }
  if (params.ENDOR_LABS_NAMESPACE) {
    args['ENDOR_LABS_NAMESPACE'] = params.ENDOR_LABS_NAMESPACE
  } else if (env.ENDOR_LABS_NAMESPACE) {
    args['ENDOR_LABS_NAMESPACE'] = env.ENDOR_LABS_NAMESPACE
  } else {
    error "ERROR: Tenant Name or Namespace should be made available as ENDOR_LABS_NAMESPACE"
  }
  if (params.SCAN_SUMMARY_OUTPUT_TYPE) {
    args['SCAN_SUMMARY_OUTPUT_TYPE'] = params.SCAN_SUMMARY_OUTPUT_TYPE
  } else if (env.SCAN_SUMMARY_OUTPUT_TYPE) {
    args['SCAN_SUMMARY_OUTPUT_TYPE'] = env.SCAN_SUMMARY_OUTPUT_TYPE
  } else {
    args['SCAN_SUMMARY_OUTPUT_TYPE'] = 'table'
  }
  if (params.LOG_LEVEL) {
    args['LOG_LEVEL'] = params.LOG_LEVEL
  } else if (env.LOG_LEVEL) {
    args['LOG_LEVEL'] = env.LOG_LEVEL
  } else {
    args['LOG_LEVEL'] = 'info'
  }
  if (params.LOG_VERBOSE) {
    args['LOG_VERBOSE'] = params.LOG_VERBOSE
  } else if (env.LOG_VERBOSE) {
    args['LOG_VERBOSE'] = env.LOG_VERBOSE
  } else {
    args['LOG_VERBOSE'] = false
  }
  if (params.LANGUAGES) {
    args['LANGUAGES'] = params.LANGUAGES
  } else if (env.LANGUAGES) {
    args['LANGUAGES'] = env.LANGUAGES
  } else {
    args['LANGUAGES'] = ''
  }
  if (params.ADDITIONAL_ARGS) {
    args['ADDITIONAL_ARGS'] = params.ADDITIONAL_ARGS
  } else if (env.ADDITIONAL_ARGS) {
    args['ADDITIONAL_ARGS'] = env.ADDITIONAL_ARGS
  } else {
    args['ADDITIONAL_ARGS'] = ''
  }
  if (params.NO_OF_THREADS) {
    args['NO_OF_THREADS'] = params.NO_OF_THREADS
  } else if (env.NO_OF_THREADS) {
    args['NO_OF_THREADS'] = env.NO_OF_THREADS
  } else {
    args['NO_OF_THREADS'] = 5
  }
  if (params.ENDOR_LABS_API) {
    args['ENDOR_LABS_API'] = params.ENDOR_LABS_API
  } else if (env.ENDOR_LABS_API) {
    args['ENDOR_LABS_API'] = env.ENDOR_LABS_API
  } else {
    args['ENDOR_LABS_API'] = ''
  }
  // if (env.GITHUB_TOKEN) {
  //   args['GITHUB_TOKEN'] = env.GITHUB_TOKEN
  // } else {
  //   error "ERROR: Github Access Token should be made available as GITHUB_TOKEN"
  // }
  // if (env.ENDOR_LABS_API_KEY) {
  //   args['ENDOR_LABS_API_KEY'] = env.ENDOR_LABS_API_KEY
  // } else {
  //   error "ERROR: Endor Labs API Key is required and should be made available as ENDOR_LABS_API_KEY"
  // }
  // if (env.ENDOR_LABS_API_SECRET) {
  //   args['ENDOR_LABS_API_SECRET'] = env.ENDOR_LABS_API_SECRET
  // } else {
  //   error "ERROR: Endor Labs API Secret is required and should be made available as ENDOR_LABS_API_SECRET"
  // }
}