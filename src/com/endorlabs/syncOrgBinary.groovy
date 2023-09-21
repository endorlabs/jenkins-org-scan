package com.endorlabs
import groovy.json.JsonSlurper

class syncOrgBinary implements Serializable {
  def call(def pipeline) {
    return execute(pipeline)
  }

  def execute(def pipeline, def args) {
    def cmd = "GITHUB_TOKEN=" + pipeline.env.GITHUB_TOKEN
    if (args['ENDORCTL_VERSION']) {
      cmd += " ENDOR_RELEASE=" + args['ENDORCTL_VERSION']
    }
    cmd += " ./endorctl"
    if (args['ENDOR_LABS_API']) {
      cmd += " --api " + args['ENDOR_LABS_API']
    }
    cmd += " --namespace " + args['ENDOR_LABS_NAMESPACE']
    cmd += " --api-key " + pipeline.env.ENDOR_LABS_API_KEY
    cmd += " --api-secret " + pipeline.env.ENDOR_LABS_API_SECRET
    cmd += " sync-org"
    if (args['GITHUB_API_URL']) {
      cmd += " --github-api-url " + pipeline.params.GITHUB_API_URL
    }
    cmd += " --name " + pipeline.params.GITHUB_ORG
    pipeline.sh(cmd)
  }

  def projectCount(def pipeline, def args) {
    def cmd = "GITHUB_TOKEN=" + pipeline.env.GITHUB_TOKEN
    if (args['ENDORCTL_VERSION']) {
      cmd += " ENDOR_RELEASE=" + args['ENDORCTL_VERSION']
    }
    cmd += " ./endorctl"
    if (args['ENDOR_LABS_API']) {
      cmd += " --api " + args['ENDOR_LABS_API']
    }
    cmd += " --namespace " + args['ENDOR_LABS_NAMESPACE']
    cmd += " --api-key " + pipeline.env.ENDOR_LABS_API_KEY
    cmd += " --api-secret " + pipeline.env.ENDOR_LABS_API_SECRET
    cmd += " api list -r Project --count"
    def jsonTxt = pipeline.sh(returnStdout: true, script: cmd).trim()
    def jsonSlurper = new JsonSlurper()
    def data = jsonSlurper.parseText(jsonTxt)
    return data.count_response.count
  }

  def getProjectList(def projects, def pipeline) {
    def cmd = "GITHUB_TOKEN=" + pipeline.env.GITHUB_TOKEN
    if (args['ENDORCTL_VERSION']) {
      cmd += " ENDOR_RELEASE=" + args['ENDORCTL_VERSION']
    }
    cmd += " ./endorctl"
    if (args['ENDOR_LABS_API']) {
      cmd += " --api " + args['ENDOR_LABS_API']
    }
    cmd += " --namespace " + args['ENDOR_LABS_NAMESPACE']
    cmd += " --api-key " + pipeline.env.ENDOR_LABS_API_KEY
    cmd += " --api-secret " + pipeline.env.ENDOR_LABS_API_SECRET
    cmd += " api list -r Project"
    def jsonTxt = pipeline.sh(returnStdout: true, script: cmd).trim()
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