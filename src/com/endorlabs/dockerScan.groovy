package com.endorlabs

class dockerScan implements Serializable {
  def call(def pipeline) {
    return execute(pipeline)
  }

  def execute(def pipeline, def args, String project, String branch) {
    def dockerRun = "docker run --rm"
    dockerRun += " -v " + pipeline.env.WORKSPACE + ":/root/endorlabs"
    dockerRun += " us-central1-docker.pkg.dev/endor-ci/public/endorctl:" + args['ENDORCTL_VERSION']
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
    dockerRun += " --enable github,git,analytics"
    dockerRun += " --repository-http-clone-url " + project
    if (branch) {
      dockerRun += " --as-default-branch --detached-ref-name=" + branch
    }
    if (args['GITHUB_API_URL']) {
      dockerRun += " --github-api-url " + args['GITHUB_API_URL']
    }
    if (args['GITHUB_CA_CERT']) {
      createCaCertFile(pipeline, args)
      dockerRun += " --github-ca-path ./caCert.pem"
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

  def createCaCertFile(def pipeline, def args) {
    pipeline.writeFile file: "caCert.pem", text: args['GITHUB_CA_CERT']
    pipeline.sh("chmod 644 ./caCert.pem")
  }
}