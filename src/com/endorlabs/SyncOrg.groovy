package com.endorlabs
import groovy.json.JsonSlurper

class SyncOrg implements Serializable {
  def call(def pipeline) {
    return execute(pipeline)
  }

  /**
   * Execute Sync-Org Command
   * 
   * This method executes the `endorctl sync-org` command to synchronize GitHub projects
   * to a specified namespace in the Endor Labs platform.
   * 
   * @param pipeline (def): The Jenkins pipeline context.
   * @param args (def): A map containing pipeline configuration parameters.
   */
  def execute(def pipeline, def args) {
    def dockerRun = "docker run --rm"
    dockerRun += " -e GITHUB_TOKEN=" + pipeline.env.GITHUB_TOKEN
    dockerRun += " us-central1-docker.pkg.dev/endor-ci/public/endorctl:" + args['ENDORCTL_VERSION']
    if (args['ENDOR_LABS_API']) {
      dockerRun += " --api " + args['ENDOR_LABS_API']
    }
    dockerRun += " --namespace " + args['ENDOR_LABS_NAMESPACE']
    dockerRun += " --api-key " + pipeline.env.ENDOR_LABS_API_KEY
    dockerRun += " --api-secret " + pipeline.env.ENDOR_LABS_API_SECRET
    dockerRun += " sync-org"
    if (args['GITHUB_API_URL']) {
      dockerRun += " --github-api-url " + args['GITHUB_API_URL']
    }
    dockerRun += " --name " + args['GITHUB_ORG']
    pipeline.sh(dockerRun)
  }

  /**
   * Get Project Count
   * 
   * This method retrieves the count of projects within a specified organization or namespace.
   * 
   * @param pipeline (def): The Jenkins pipeline context.
   * @param args (def): A map containing pipeline configuration parameters.
   * @return: The count of projects.
   */
  def getProjectCount(def pipeline, def args) {
    def dockerRun = "docker run --rm"
    dockerRun += " -e GITHUB_TOKEN=" + pipeline.env.GITHUB_TOKEN
    dockerRun += " us-central1-docker.pkg.dev/endor-ci/public/endorctl:" + args['ENDORCTL_VERSION']
    if (args['ENDOR_LABS_API']) {
      dockerRun += " --api " + args['ENDOR_LABS_API']
    }
    dockerRun += " --namespace " + args['ENDOR_LABS_NAMESPACE']
    dockerRun += " --api-key " + pipeline.env.ENDOR_LABS_API_KEY
    dockerRun += " --api-secret " + pipeline.env.ENDOR_LABS_API_SECRET
    dockerRun += " api list -r Project --count"
    def jsonTxt = pipeline.sh(returnStdout: true, script: dockerRun).trim()
    def jsonSlurper = new JsonSlurper()
    def data = jsonSlurper.parseText(jsonTxt)
    return data.count_response.count
  }

  /**
   * Get Project List
   * 
   * This method retrieves the list of projects within a specified organization or namespace.
   * Optionally, it can exclude specific projects from the list.
   * 
   * @param projects (def): A list to store the retrieved project URLs.
   * @param pipeline (def): The Jenkins pipeline context.
   * @param args (def): A map containing pipeline configuration parameters.
   */
  def getProjectList(def projects, def pipeline, def args) {
    def dockerRun = "docker run --rm"
    dockerRun += " -e GITHUB_TOKEN=" + pipeline.env.GITHUB_TOKEN
    dockerRun += " us-central1-docker.pkg.dev/endor-ci/public/endorctl:" + args['ENDORCTL_VERSION']
    if (args['ENDOR_LABS_API']) {
      dockerRun += " --api " + args['ENDOR_LABS_API']
    }
    dockerRun += " --namespace " + args['ENDOR_LABS_NAMESPACE']
    dockerRun += " --api-key " + pipeline.env.ENDOR_LABS_API_KEY
    dockerRun += " --api-secret " + pipeline.env.ENDOR_LABS_API_SECRET
    dockerRun += " api list -r Project"
    def exclude_projects = []
    if (args['EXCLUDE_PROJECTS']) {
      def projectList = args['EXCLUDE_PROJECTS'].strip().split('\n')
      for (String project: projectList) {
        if (project) {
          exclude_projects.add(project.strip())
        }
      }
    }
    def jsonTxt = pipeline.sh(returnStdout: true, script: dockerRun).trim()
    def jsonSlurper = new JsonSlurper()
    def data = jsonSlurper.parseText(jsonTxt)
    def objects = data.list.objects
    objects.each { entry ->
      if (entry.spec.git && entry.spec.git.http_clone_url) {
        if (exclude_projects.contains(entry.spec.git.http_clone_url)) {
          pipeline.echo("Excluding ${entry.spec.git.http_clone_url} from the list of projects to scan")
        } else {
          projects.add(entry.spec.git.http_clone_url)
        }
      }
    }
  }
}