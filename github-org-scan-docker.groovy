@Library("endor-shared-lib") _
import groovy.json.JsonSlurper
import java.text.SimpleDateFormat
import com.endorlabs.DockerScan
import com.endorlabs.SyncOrg
import com.endorlabs.Checkout
def DockerScan = new DockerScan()
def SyncOrg = new SyncOrg()
def args = [:]
getParameters(args)
def projects = []

def extractRepoFromGitURL(projectUrl) {
  // Extract the path part of the URL
  def path = new URL(projectUrl).path
  // Remove leading and trailing slashes
  path = path = path.replaceAll('^/|/$', '').replaceAll('\\.git$', '')
  println "Extracted path: ${path}"
  return path
}

// Define a function to check if the latest commit is newer than one week
def isCommitNewerThanOneWeek(projectUrl) {
    def dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    def oneWeekAgo = new Date() - 7

    def repo = extractRepoFromGitURL(projectUrl)

    def apiUrl = new URL("https://api.github.com/repos/$repo/commits?per_page=1")
    def response = apiUrl.getText()
    def json = new JsonSlurper().parseText(response)
    def commitDate = json[0].commit.author.date
    def commitTimeStampInLastOneWeek = false
    if(json[0].commit.author.date) {
      echo "Commit date is present in JSON format"
      def commitTimestamp = dateFormat.parse(commitDate)
      echo "For project: ${projectUrl} the newer commit flag is ${commitTimestamp.after(oneWeekAgo)}"
    }
    
    return commitTimestamp.after(oneWeekAgo)
}

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
          if (args['PROJECT_LIST']) {
            echo "Skipping 'sync-org' as Project List is provided"
            def projectList = args['PROJECT_LIST'].strip().split('\n')
            for (String project: projectList) {
              if (project) {
                projects.add(project.strip())
              }
            }
            def projectCount = projects.size()
            echo "Project Count: ${projectCount}"
          } else {
            SyncOrg.execute(this, args)
            def projectCount = SyncOrg.getProjectCount(this, args)
            echo "Project Count: ${projectCount}"
          }
        }
      }
    }
    stage("Get Project List") {
      steps {
        script {
          if (!args['PROJECT_LIST']) {
            SyncOrg.getProjectList(projects, this, args)
          }
          echo "List of Projects:\n" + projects.join("\n")
          if (args['SCAN_PROJECTS_COMMITS_ONE_WEEK'].toBoolean()) {
            echo "Cleaning up projects older than a week\n"
            projects.removeAll { item -> !isCommitNewerThanOneWeek(item) }
            echo "List of Projects after cleanup:\n" + projects.join("\n")            
          } else {
            echo "Commit time check not performed. Parameter was not enabled."
          }
        }
      }
    }
    stage("Trigger Parallel Scan") {
      steps {
        script {
          def parallellExecutor = args['NO_OF_THREADS'].toInteger()
          echo "No. of Parallel Scans allowed: " + args['NO_OF_THREADS']
          def repository_group = projects.collate parallellExecutor
          for (def repos : repository_group) {
            def targets = [:]
            for (String project : repos) {
              generate_scan_stages(targets, project, args)
            }
            parallel targets
          }
        }
      }
    }
  }
}

/**
 * Generate Scan Stages
 * 
 * This function generates Jenkins pipeline stages for running `endorctl` scans on a specified set of targets.
 * Each stage represents a scan job for a specific project within a Jenkins pipeline.
 * 
 * @param targets (Map<String, Map>): A map where the keys are project names and the values are maps
 *   containing the configuration for each project.
 * 
 * @param project (String): The URL of the target repository or project to scan.
 * 
 * @param args (Map<String, String>): Arguments to pass to the Jenkins pipeline stages.
 * 
 * @return (Map<String, Map>): A map representing the generated Jenkins pipeline stages.
 *   Each stage corresponds to a project and includes the steps required to scan the project.
 */

def generate_scan_stages(def targets, def project, def args) {
  if (project) {
    targets[project] = {
      def DockerScan = new DockerScan()
      def Checkout = new Checkout()
      String projectName = projectName(project)
      String stageName = "Scan " + projectName
      node(args['AGENT_LABEL']) {
        stage(stageName) {
          try {
            String workspace = Checkout.getWorkSpace(this, project)
            Checkout.setCredentialHelper(this)
            Checkout.clone(this, args, project, workspace)
            def branch = Checkout.getDefaultBranch(this, project, workspace)
            Checkout.execute(this, branch, workspace)
            DockerScan.execute(this, args, project, branch, workspace)
          } catch (err) {
            echo err.toString()
            unstable("endorctl Scan failed for ${project}")
          }
        }
      }
    }
  }
}

/**
 * Get Project Name from URL
 * 
 * This function extracts the project name from a GitHub repository URL.
 * It is designed to work with GitHub repository URLs in the format:
 * `https://github.com/organization/repository.git`.
 * 
 * @param project (String): The URL of the GitHub repository.
 * 
 * @return (String): The extracted project name, which is the combination of
 *   the organization or user name and the repository name (e.g., "organization/repository").
 *   If the input URL is not in the expected format, the original input project string is returned.
 * 
 * Example Usage:
 * String projectURL = 'https://github.com/example/my-repo.git'
 * String projectName = projectName(projectURL)
 * 
 */
def projectName(String project) {
  def matcher = project =~ /^https:\/\/([a-zA-Z\-\.]+)\/(?<proj>[a-zA-Z\-_]+\/[a-zA-Z\-_]+)\.git$/
  if (matcher.matches()) {
    return matcher.group("proj")
  } else {
    return project
  }
}

/**
 * Get Pipeline Parameters
 * 
 * This function retrieves pipeline parameters from both the Jenkins pipeline's parameters
 * and environment variables. It populates the `args` map with the specified parameters,
 * ensuring that the required parameters have values. If a parameter is not provided,
 * it falls back to environment variables or defaults.
 * 
 * @param args (Map): A map representing the configuration parameters for the pipeline.
 *   The map is structured as follows:
 *   {
 *     'AGENT_LABEL': 'Label of Jenkins Agent',
 *     'GITHUB_ORG': 'GitHub organization name',
 *     'ENDORCTL_VERSION': 'Version of endorctl Docker container',
 *     'ENDOR_LABS_NAMESPACE': 'Endor Labs platform namespace',
 *     'SCAN_SUMMARY_OUTPUT_TYPE': 'Summary output format',
 *     'LOG_LEVEL': 'Log level for the application',
 *     'LOG_VERBOSE': 'Whether to enable verbose logging (true/false)',
 *     'LANGUAGES': 'Programming languages to scan',
 *     'ADDITIONAL_ARGS': 'Additional arguments for scan',
 *     'NO_OF_THREADS': 'Number of Jenkins Agents to use in parallel',
 *     'ENDOR_LABS_API': 'Endor Labs API Key',
 *     'GITHUB_API_URL': 'GitHub Enterprise Server API URL',
 *     'GITHUB_CA_CERT': 'GitHub Enterprise Server CA Certificate',
 *     'GITHUB_DISABLE_SSL_VERIFY': 'Whether to disable SSL verification (true/false)',
 *     'PROJECT_LIST': 'List of projects/repositories to scan',
 *     'SCAN_TYPE': 'Scan source (git/github)',
 *     'EXCLUDE_PROJECTS': 'List of projects to exclude from scan',
 *   }
 * 
 * @throws error: Throws an error and terminates the pipeline if mandatory parameters are missing.
 * 
 * Example Usage:
 * def args = [:]
 * getParameters(args)
 * 
 * The 'args' map will be populated with the retrieved or default parameter values.
 * Missing mandatory parameters will result in an error and pipeline termination.
 */
def getParameters(def args) {
  if (params.AGENT_LABEL) {
    args['AGENT_LABEL'] = params.AGENT_LABEL
  } else if (env.AGENT_LABEL) {
    args['AGENT_LABEL'] = env.AGENT_LABEL
  } else {
    error "ERROR: Agent Label is Mandatory. It should made available as AGENT_LABEL"
  }
  if (params.GITHUB_ORG) {
    args['GITHUB_ORG'] = params.GITHUB_ORG
  } else if (env.GITHUB_ORG) {
    args['GITHUB_ORG'] = env.GITHUB_ORG
  } else {
    error "ERROR: Github Org Info is Mandatory. It should be made available as GITHUB_ORG"
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
  }
  if (params.ADDITIONAL_ARGS) {
    args['ADDITIONAL_ARGS'] = params.ADDITIONAL_ARGS
  } else if (env.ADDITIONAL_ARGS) {
    args['ADDITIONAL_ARGS'] = env.ADDITIONAL_ARGS
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
  }
  if (params.GITHUB_API_URL) {
    args['GITHUB_API_URL'] = params.GITHUB_API_URL
  } else if (env.GITHUB_API_URL) {
    args['GITHUB_API_URL'] = env.GITHUB_API_URL
  }
  if (params.GITHUB_CA_CERT) {
    args['GITHUB_CA_CERT'] = params.GITHUB_CA_CERT
  } else if (env.GITHUB_CA_CERT) {
    args['GITHUB_CA_CERT'] = env.GITHUB_CA_CERT
  }
  if (params.GITHUB_DISABLE_SSL_VERIFY) {
    args['GITHUB_DISABLE_SSL_VERIFY'] = params.GITHUB_DISABLE_SSL_VERIFY
  } else if (env.GITHUB_DISABLE_SSL_VERIFY) {
    args['GITHUB_DISABLE_SSL_VERIFY'] = env.GITHUB_DISABLE_SSL_VERIFY
  }
  if (params.PROJECT_LIST) {
    args['PROJECT_LIST'] = params.PROJECT_LIST
  } else if (env.PROJECT_LIST) {
    args['PROJECT_LIST'] = env.PROJECT_LIST
  }
  if (params.SCAN_TYPE) {
    args['SCAN_TYPE'] = params.SCAN_TYPE
  } else if (env.SCAN_TYPE) {
    args['SCAN_TYPE'] = env.SCAN_TYPE
  }
  if (params.EXCLUDE_PROJECTS) {
    args['EXCLUDE_PROJECTS'] = params.EXCLUDE_PROJECTS
  } else if (env.EXCLUDE_PROJECTS) {
    args['EXCLUDE_PROJECTS'] = env.EXCLUDE_PROJECTS
  }
  if(params.SCAN_PROJECTS_COMMITS_ONE_WEEK) {
    args['SCAN_PROJECTS_COMMITS_ONE_WEEK'] = params.SCAN_PROJECTS_COMMITS_ONE_WEEK
  } else {
    args['SCAN_PROJECTS_COMMITS_ONE_WEEK'] = env.SCAN_PROJECTS_COMMITS_ONE_WEEK
  }
}
