package com.endorlabs

class endorctlScan implements Serializable {
  private String hostName

  def call(def pipeline) {
    return execute(pipeline)
  }

  def execute(def pipeline, def args, String branch) {
    String cmd = ""
    pipeline.echo("Helllo!")
    pipeline.echo(cmd)
    if (args['ENDORCTL_VERSION']) {
      cmd += "ENDOR_RELEASE=" + args['ENDORCTL_VERSION'] + " " 
    }
    cmd += "./endorctl"
    if (args['ENDOR_LABS_API']) {
      cmd += " --api " + args['ENDOR_LABS_API']
    }
    pipeline.echo(cmd)
    cmd += " --namespace " + args['ENDOR_LABS_NAMESPACE']
    cmd += " --api-key " + pipeline.env.ENDOR_LABS_API_KEY
    cmd += " --api-secret " + pipeline.env.ENDOR_LABS_API_SECRET
    if (args['LOG_VERBOSE']) {
      cmd += " --verbose"
    }
    pipeline.echo(cmd)
    cmd += " --log-level " + args['LOG_LEVEL']
    cmd += " scan --path=" + + pipeline.env.WORKSPACE
    cmd += " --github-token " + pipeline.env.GITHUB_TOKEN
    pipeline.echo(cmd)
    if (args['GITHUB_API_URL']) {
      cmd += " --github-api-url " + args['GITHUB_API_URL']
    }
    pipeline.echo(cmd)
    if (branch) {
      cmd += " --as-default-branch --detached-ref-name=" + branch
    }
    pipeline.echo(cmd)
    cmd += " --output-type " + args['SCAN_SUMMARY_OUTPUT_TYPE']
    if (args['LANGUAGES']) {
      cmd += " --languages " + args['LANGUAGES']
    }
    pipeline.echo(cmd)
    if (args['ADDITIONAL_ARGS']) {
      cmd += " " + args['ADDITIONAL_ARGS']
    }
    pipeline.echo(cmd)
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