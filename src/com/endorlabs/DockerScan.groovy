package com.endorlabs

class DockerScan implements Serializable {
  def call(def pipeline) {
    return execute(pipeline)
  }

  /**
  * Execute Endorctl Scan
  * 
  * This function executes the `endorctl scan` command within a Docker container to perform code scanning
  * on a specified GitHub project and branch within a Jenkins workspace.
  * 
  * @param pipeline (def): The Jenkins pipeline context.
  * @param args (def): A map containing pipeline configuration parameters.
  * @param project (String): The HTTP clone URL of the GitHub project to scan.
  * @param branch (String): The branch name to scan, or null if the default branch should be used.
  * @param workspace (String): The path to the Jenkins workspace where the project is checked out.
  * 
  * This function will execute the 'endorctl scan' command within a Docker container
  * to perform code scanning on the specified GitHub project and branch.
  */
  def execute(def pipeline, def args, String project, String branch, String workspace) {
    def dockerRun = "docker run --rm"
    dockerRun += " -v '" + workspace + "':/root/endorlabs"
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
    if (args['SCAN_TYPE'] && args['SCAN_TYPE'].contains("github")) {
      dockerRun += " --github-token " + pipeline.env.GITHUB_TOKEN
    }
    if (args['SCAN_TYPE']) {
      dockerRun += " --enable " + args['SCAN_TYPE']
    }
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
    pipeline.echo("Running endorctl scan for path '$workspace' on host '$hostName'")
    pipeline.sh(dockerRun)
  }

  /**
  * Create CA Certificate File
  * 
  * This function creates a CA certificate file from the provided CA certificate content
  * and sets the file permissions to ensure it's readable.
  * 
  * @param pipeline (def): The Jenkins pipeline context.
  * @param args (def): A map containing pipeline configuration parameters.
  *   - 'GITHUB_CA_CERT' (String): The content of the GitHub Enterprise Server CA certificate in PEM format.
  * 
  * Example Usage:
  * def args = [
  *   'GITHUB_CA_CERT': '-----BEGIN CERTIFICATE-----\n... (Certificate Content) ...\n-----END CERTIFICATE-----'
  * ]
  * createCaCertFile(currentBuild, args)
  * 
  * This function will create a 'caCert.pem' file with the provided CA certificate content
  * and set the file permissions to make it readable.
  */
  def createCaCertFile(def pipeline, def args) {
    pipeline.writeFile file: "caCert.pem", text: args['GITHUB_CA_CERT']
    pipeline.sh("chmod 644 ./caCert.pem")
  }
}
