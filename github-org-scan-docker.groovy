@Library("endor-shared-lib") _
import groovy.json.JsonSlurper
import java.text.SimpleDateFormat
import java.util.TimeZone
import com.endorlabs.DockerScan
import com.endorlabs.SyncOrg
import com.endorlabs.Checkout

def DockerScan = new DockerScan()
def SyncOrg = new SyncOrg()
def args = [:]
def projectsWithUUID = [:]
getParameters(args)
def projects = []

pipeline {
    agent {
        label args['AGENT_LABEL']
    }
    options {
        timestamps()
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
                        for (String project : projectList) {
                            if (project) {
                                projects.add(project.strip())
                            }
                        }
                        projects = projects.unique()
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
                        SyncOrg.getProjectList(projects, this, args, projectsWithUUID)
                    }
                    echo "List of Projects:\n" + projects.join("\n")
                    if (args['SCAN_PROJECTS_BY_LAST_COMMIT'].toInteger() > 0) {
                        echo "Cleaning up projects older than ${args['SCAN_PROJECTS_BY_LAST_COMMIT'].toInteger()} days"
                        projects = filterProjects(this, projects, args, projectsWithUUID)
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
                    if (args['ENABLE_GITHUB_RATE_LIMIT_DEBUG']) {
                        echo "Github rate limits before scan starts..."
                        printGitHubRateLimit(this)
                    }
                    try {
                        String workspace = Checkout.getWorkSpace(this, project)
                        Checkout.setCredentialHelper(this)
                        Checkout.clone(this, args, project, workspace, false)
                        def branch = Checkout.getDefaultBranch(this, project, workspace)
                        Checkout.execute(this, branch, workspace)
                        DockerScan.execute(this, args, project, branch, workspace)
                    } catch (err) {
                        echo err.toString()
                        unstable("endorctl Scan failed for ${project}")
                    }

                    if (args['ENABLE_GITHUB_RATE_LIMIT_DEBUG']) {
                        echo "Github rate limits after scan completed..."
                        printGitHubRateLimit(this)
                    }
                }
            }
        }
    }
}

def filterProjects(def pipeline, def projects, def args, def projectsWithUUID) {
    def scannableProjects = []
    setGHCreds(this)

    // creating tmp path to clone repo to fetch commitSHA
    String tmpDir = '"' + pipeline.env.WORKSPACE + '/endor_tmp"'
    pipeline.sh("mkdir ${tmpDir}")

    int batch_size = args['CLONE_BATCH_SIZE'].toInteger()
    int sleep_time = args['CLONE_SLEEP_SECONDS'].toInteger()
    int i = 0

    for (p in projects) {
        String wp = "${tmpDir}" + "/" + getRepoFullName(p)
        wp = "\"${wp}\""

        if (projectHasCommitsWithinLastNDays(pipeline, p, args, projectsWithUUID, wp)) {
            scannableProjects.add(p)
        }
        i++
        if (i == batch_size) {
            sleep(time: sleep_time, unit: "SECONDS")
            i = 0
        }

        // clean cloned repo after we are done comparing dates.
        pipeline.sh("cd ${tmpDir} && rm -rf *")
    }

    // clean up the tmp repo in case something is left from cloned repos.
    pipeline.sh("cd ${tmpDir} && rm -rf *")
    return scannableProjects
}

/**
 *
 * @param url
 * @param args
 * @param projectsWithUUID
 * @return
 */
def projectHasCommitsWithinLastNDays(def pipeline, String projURL, def args, def projectsWithUUID, String wp) {
    def numberOfDays = args['SCAN_PROJECTS_BY_LAST_COMMIT'].toInteger()

    def dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX")
    def utcTimeFormat = getUTCTimeFormat()
    def curUTCTime = dateFormat.parse(utcTimeFormat.format(new Date()))

    def nDaysAgo = curUTCTime - numberOfDays
    def hasCommitInLastNDays = false

    def Checkout = new Checkout()
    Checkout.clone(this, args, projURL, wp, true)

    if (args['ENABLE_GITHUB_RATE_LIMIT_DEBUG']) {
        printGitHubRateLimit(pipeline)
    }

    data = getLastCommitData(this, wp)
    String[] commitInfo = data.strip().split("\n")
    if (commitInfo.size() == 2) {
        commitDate = commitInfo[0]
        commitSHA = commitInfo[1]
        echo "For project: ${projURL} last commit date is: ${commitDate} and commitSHA is: ${commitSHA}"
        def commitTimestamp = utcTimeFormat.format(dateFormat.parse(commitDate))
        echo "Comparing dates, include commit after date: ${nDaysAgo} and last commit date: ${commitTimestamp}"
        hasCommitInLastNDays = dateFormat.parse(commitTimestamp).after(nDaysAgo)

        if (hasCommitInLastNDays) {
            // This project has commits within time limit passed via SCAN_PROJECTS_BY_LAST_COMMIT,
            // lets check if the said commit is already scanned.
            echo "Checking if the commit: ${commitSHA} for project ${projURL} is already scanned."
            String repoVerUUID = getScannedRepoVersionWithCommit(this, args, projURL, commitSHA, projectsWithUUID)
            if (repoVerUUID?.trim()) {
                echo "Commit ${commitSHA} for project: ${projURL} is already scanned with RepositoryVersion UUID = ${repoVerUUID}, skipping the scan of this project."
                hasCommitInLastNDays = false
            } else {
                echo "Commit ${commitSHA} for project: ${projURL} is not yet scanned and will be scanned."
            }
        }
    }

    echo "For project: ${projURL} the newer commit flag is ${hasCommitInLastNDays}"
    return hasCommitInLastNDays
}

def setGHCreds(def pipeline) {
    def Checkout = new Checkout()
    Checkout.setCredentialHelper(this)
}

def getRepoFullName(String projURL) {
    String orgRepo = projURL.replaceAll('^https://github.com/|\\.git$', '')
    orgRepo = orgRepo.replaceAll('\\_/', '-')
    return orgRepo
}

def getLastCommitData(def pipeline, String workspace) {
    def lastCommitInfoCmd = 'cd "' + workspace + '" &&'
    lastCommitInfoCmd += ' git log -1 --pretty=format:%aI%n%H'
    def commitDate = pipeline.sh(returnStdout: true, script: lastCommitInfoCmd).trim()
    return commitDate
}

def getUTCTimeFormat() {
    def dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX")
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
    return dateFormat
}

def getScannedRepoVersionWithCommit(def pipeline, def args, String project, String commit, def projectsWithUUID) {
    String scannedRepoVersionUUID = ""
    String projUUID = ""
    if (!projectsWithUUID.containsKey("${project}")) {
        echo "Fething project UUID from DB."
        projUUID = getProjectUUID(pipeline, args, project)
    } else {
        projUUID = projectsWithUUID["${project}"]
    }

    if (!projUUID?.trim()) {
        return scannedRepoVersionUUID
    }

    echo "Verifying repository version scan status for ${commit} commit with project uuid= ${projUUID}."
    def repoList = getRepositoryVersionList(pipeline, args, projUUID)
    repoList.each { entry ->
        if (entry.scan_object.status == "STATUS_SCANNED" && entry.spec.version.sha == commit) {
            scannedRepoVersionUUID = entry.uuid
        }
    }

    return scannedRepoVersionUUID
}

def getProjectUUID(def pipeline, def args, String project) {
    def dockerRun = "docker run --rm"
    dockerRun += " us-central1-docker.pkg.dev/endor-ci/public/endorctl:" + args['ENDORCTL_VERSION']
    if (args['ENDOR_LABS_API']) {
        dockerRun += " --api " + args['ENDOR_LABS_API']
    }
    dockerRun += " --namespace " + args['ENDOR_LABS_NAMESPACE']
    dockerRun += " --api-key " + pipeline.env.ENDOR_LABS_API_KEY
    dockerRun += " --api-secret " + pipeline.env.ENDOR_LABS_API_SECRET
    dockerRun += " api list -r Project --filter=\"spec.git.http_clone_url==${project}\""
    def jsonTxt = pipeline.sh(returnStdout: true, script: dockerRun).trim()
    def jsonSlurper = new JsonSlurper()
    def data = jsonSlurper.parseText(jsonTxt)
    def objects = data.list.objects

    String uuid = ""
    objects.each { entry ->
        uuid = entry.uuid
    }

    return uuid
}

def getRepositoryVersionList(def pipeline, def args, String uuid) {
    def dockerRun = "docker run --rm"
    dockerRun += " us-central1-docker.pkg.dev/endor-ci/public/endorctl:" + args['ENDORCTL_VERSION']
    if (args['ENDOR_LABS_API']) {
        dockerRun += " --api " + args['ENDOR_LABS_API']
    }
    dockerRun += " --namespace " + args['ENDOR_LABS_NAMESPACE']
    dockerRun += " --api-key " + pipeline.env.ENDOR_LABS_API_KEY
    dockerRun += " --api-secret " + pipeline.env.ENDOR_LABS_API_SECRET
    dockerRun += " api list -r RepositoryVersion"
    dockerRun += " --filter=\"meta.parent_uuid==${uuid}\""
    dockerRun += " --field-mask=spec.version,scan_object"

    def jsonTxt = pipeline.sh(returnStdout: true, script: dockerRun).trim()
    def jsonSlurper = new JsonSlurper()
    def data = jsonSlurper.parseText(jsonTxt)

    return data.list.objects
}

def printGitHubRateLimit(def pipeline) {
    def token = env.GITHUB_TOKEN
    def curl_cmd = "curl -L \\"
    curl_cmd += "-H \"Accept: application/vnd.github+json\" \\"
    curl_cmd += "-H \"Authorization: Bearer ${token}\" \\"
    curl_cmd += "-H \"X-GitHub-Api-Version: 2022-11-28\" \\"
    curl_cmd += "https://api.github.com/rate_limit"

    def remaining_limits = pipeline.sh(returnStdout: true, script: curl_cmd).trim()
    echo "remaining github rate limits"
    echo "${remaining_limits}"
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
    if (params.SCAN_PROJECTS_BY_LAST_COMMIT) {
        args['SCAN_PROJECTS_BY_LAST_COMMIT'] = params.SCAN_PROJECTS_BY_LAST_COMMIT
    } else if (env.SCAN_PROJECTS_BY_LAST_COMMIT) {
        args['SCAN_PROJECTS_BY_LAST_COMMIT'] = env.SCAN_PROJECTS_BY_LAST_COMMIT
    } else {
        args['SCAN_PROJECTS_BY_LAST_COMMIT'] = 0
    }

    if (params.CLONE_BATCH_SIZE) {
        args['CLONE_BATCH_SIZE'] = params.CLONE_BATCH_SIZE
    } else {
        args['CLONE_BATCH_SIZE'] = 3
    }

    if (params.CLONE_SLEEP_SECONDS) {
        args['CLONE_SLEEP_SECONDS'] = params.CLONE_SLEEP_SECONDS
    } else {
        args['CLONE_SLEEP_SECONDS'] = 2
    }

    if (params.ENABLE_GITHUB_RATE_LIMIT_DEBUG) {
        args['ENABLE_GITHUB_RATE_LIMIT_DEBUG'] = params.ENABLE_GITHUB_RATE_LIMIT_DEBUG
    } else {
        args['ENABLE_GITHUB_RATE_LIMIT_DEBUG'] = false
    }
}
