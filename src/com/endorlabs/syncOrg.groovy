package com.endorlabs
import groovy.json.JsonSlurper

class syncOrg implements Serializable {
  def call(def pipeline) {
    return execute(pipeline)
  }

  def execute(def pipeline) {
    def dockerRun = "docker run --rm"
    dockerRun += " -e GITHUB_TOKEN=" + pipeline.env.GITHUB_TOKEN
    dockerRun += " us-central1-docker.pkg.dev/endor-ci/public/endorctl:" + pipeline.params.ENDORCTL_VERSION
    dockerRun += " --api " + pipeline.params.ENDOR_LABS_API
    dockerRun += " --namespace " + pipeline.params.ENDOR_LABS_NAMESPACE
    dockerRun += " --api-key " + pipeline.env.ENDOR_LABS_API_KEY 
    dockerRun += " --api-secret " + pipeline.env.ENDOR_LABS_API_SECRET
    dockerRun += " sync-org"
    if (pipeline.params.GITHUB_API_URL) {
      dockerRun += " --github-api-url " + pipeline.params.GITHUB_API_URL
    } else if ( pipeline.env.GITHUB_API_URL ) {
      dockerRun += " --github-api-url " + pipeline.env.GITHUB_API_URL
    }
    if (pipeline.params.GITHUB_ORG) {
      dockerRun += " --name " + pipeline.params.GITHUB_ORG
    } else if (pipeline.env.GITHUB_ORG) {
      dockerRun += " --name " + pipeline.env.GITHUB_ORG
    } else {
      pipeline.currentBuild.result = 'FAILURE'
      pipeline.error "GITHUB_ORG should be specified as a Parameter or Environment Variable"
    }
    
    pipeline.sh(dockerRun)
  }

  def projectCount(def pipeline) {
    def dockerRun = "docker run --rm"
    dockerRun += " -e GITHUB_TOKEN=" + pipeline.env.GITHUB_TOKEN
    dockerRun += " us-central1-docker.pkg.dev/endor-ci/public/endorctl:" + pipeline.params.ENDORCTL_VERSION
    dockerRun += " --api " + pipeline.params.ENDOR_LABS_API
    dockerRun += " --namespace " + pipeline.params.ENDOR_LABS_NAMESPACE
    dockerRun += " --api-key " + pipeline.env.ENDOR_LABS_API_KEY 
    dockerRun += " --api-secret " + pipeline.env.ENDOR_LABS_API_SECRET
    dockerRun += " api list -r Project --count"
    def jsonTxt = pipeline.sh(returnStdout: true, script: dockerRun).trim()
    def jsonSlurper = new JsonSlurper()
    def data = jsonSlurper.parseText(jsonTxt)
    return data.count_response.count
  }

  def getProjectList(def projects, def pipeline) {
    def dockerRun = "docker run --rm"
    dockerRun += " -e GITHUB_TOKEN=" + pipeline.env.GITHUB_TOKEN
    dockerRun += " us-central1-docker.pkg.dev/endor-ci/public/endorctl:" + pipeline.params.ENDORCTL_VERSION
    dockerRun += " --api " + pipeline.params.ENDOR_LABS_API
    dockerRun += " --namespace " + pipeline.params.ENDOR_LABS_NAMESPACE
    dockerRun += " --api-key " + pipeline.env.ENDOR_LABS_API_KEY 
    dockerRun += " --api-secret " + pipeline.env.ENDOR_LABS_API_SECRET
    dockerRun += " api list -r Project"
    def jsonTxt = pipeline.sh(returnStdout: true, script: dockerRun).trim()
    def jsonSlurper = new JsonSlurper()
    def data = jsonSlurper.parseText(jsonTxt)
    def objects = data.list.objects
    for (def item : objects) {
      for (def entry : item) {
        String key = entry.getKey();
        def value = entry.getValue();
        if ( key == "spec" ) {
            projects.add(value.git.http_clone_url)
        }
      }
    }
  }
}