package com.endorlabs
import groovy.json.JsonSlurper

class syncOrg implements Serializable {
  def call(def pipeline) {
    return execute(pipeline)
  }

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