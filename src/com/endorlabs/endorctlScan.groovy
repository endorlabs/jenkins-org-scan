package com.endorlabs

class endorctlScan implements Serializable {
  private String hostName

  def call(def pipeline) {
    return execute(pipeline)
  }

  def execute(def pipeline, def args, String branch) {
    def cmd = ""
    if (args['ENDORCTL_VERSION']) {
      cmd += "ENDOR_RELEASE=" + args['ENDORCTL_VERSION'] + " " 
    } 
    cmd += "./endorctl"
    if (args['ENDOR_LABS_API']) {
      cmd += " --api " + args['ENDOR_LABS_API']
    }
    cmd += " --namespace " + args['ENDOR_LABS_NAMESPACE']
    cmd += " --api-key " + pipeline.env.ENDOR_LABS_API_KEY
    cmd += " --api-secret " + pipeline.env.ENDOR_LABS_API_SECRET
    if (args['LOG_VERBOSE']) {
      cmd += " --verbose"
    }
    cmd += " --log-level " + args['LOG_LEVEL']
    cmd += " scan --path=" + + pipeline.env.WORKSPACE
    cmd += " --github-token " + pipeline.env.GITHUB_TOKEN
    if (args['GITHUB_API_URL']) {
      dockerRun += " --github-api-url " + args['GITHUB_API_URL']
    }
    if (branch) {
      cmd += " --as-default-branch --detached-ref-name=" + branch
    }
    cmd += " --output-type " + args['SCAN_SUMMARY_OUTPUT_TYPE']
    if (args['LANGUAGES']) {
      cmd += " --languages " + args['LANGUAGES']
    }
    if (args['ADDITIONAL_ARGS']) {
      cmd += " " + args['ADDITIONAL_ARGS']
    }
    def hostName = pipeline.sh(returnStdout: true, script: "uname -n").trim()
    def path = pipeline.sh(returnStdout: true, script: "pwd").trim()
    pipeline.echo("Running endorctl scan in $path on $hostName")
    pipeline.sh(cmd)
  }

  def runHostCheck(def pipeline, def args) {
    def cmd = ""
    if (args['ENDORCTL_VERSION']) {
      cmd += "ENDOR_RELEASE=" + args['ENDORCTL_VERSION'] + " " 
    } 
    cmd += "./endorctl"
    if (args['ENDOR_LABS_API']) {
      cmd += " --api " + args['ENDOR_LABS_API']
    }
    cmd += " --namespace " + args['ENDOR_LABS_NAMESPACE']
    cmd += " --api-key " + pipeline.env.ENDOR_LABS_API_KEY
    cmd += " --api-secret " + pipeline.env.ENDOR_LABS_API_SECRET
    cmd += " host-check"
    if (! hostName) {
      hostName = pipeline.sh(returnStdout: true, script: "uname -n").trim()
    }
    pipeline.echo("Running endorctl host-chek on $hostName")
    pipeline.sh(cmd)
  }

  def uploadEndorctl(def pipeline) {
    def endorctl = pipeline.libraryResource "com/endorlabs/endorctl"
    pipeline.writeFile file: "endorctl", text: endorctl
    pipeline.sh("chmod a+x ./endorctl")
  }
}