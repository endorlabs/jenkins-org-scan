package com.endorlabs

class dockerScan implements Serializable {
  def call(def pipeline) {
    return execute(pipeline)
  }

  def execute(def pipeline, def args, String branch) {
    def dockerRun = "docker run --rm"
    dockerRun += " -v " + pipeline.env.WORKSPACE + ":/root/endorlabs"
    dockerRun += " us-central1-docker.pkg.dev/endor-ci/public/endorctl:" + pipeline.params.ENDORCTL_VERSION
    if (args['ENDOR_LABS_API']) {
      dockerRun += " --api " + args['ENDOR_LABS_API']
    }
    dockerRun += " --namespace " + args['ENDOR_LABS_NAMESPACE']
    dockerRun += " --api-key " + pipeline.env.ENDOR_LABS_API_KEY
    dockerRun += " --api-secret " + pipeline.env.ENDOR_LABS_API_SECRET
    if (args['LOG_VERBOSE']) {
      dockerRun += " --verbose"
    }
    dockerRun += " --log-level " + args['LOG_LEVEL'] + " scan --path=/root/endorlabs "
    dockerRun += " --github-token " + pipeline.env.GITHUB_TOKEN
    if (branch) {
      dockerRun += " --as-default-branch --detached-ref-name=" + branch
    }
    dockerRun += " --output-type " + args['SCAN_SUMMARY_OUTPUT_TYPE']
    if (args['LANGUAGES']) {
      dockerRun += " --languages " + args['LANGUAGES']
    }
    if (args['ADDITIONAL_ARGS']) {
      dockerRun += " " + args['ADDITIONAL_ARGS']
    }
    def hostName = pipeline.sh(returnStdout: true, script: "uname -n").trim()
    def path = pipeline.sh(returnStdout: true, script: "pwd").trim()
    pipeline.echo("Running endorctl scan in $path on $hostName")
    pipeline.sh(dockerRun)
  }
}