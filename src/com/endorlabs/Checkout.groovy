package com.endorlabs
import groovy.json.JsonSlurper

class Checkout implements Serializable {
  def call(def pipeline, String url) {
    return https(pipeline, url)
  }

  /**
  * Clone Git Repository
  * 
  * This function clones a Git repository from the specified URL into the Jenkins workspace.
  * It can optionally disable SSL verification based on pipeline configuration.
  * 
  * @param pipeline (def): The Jenkins pipeline context.
  * @param args (Map<String, Map>): A map containing pipeline configuration parameters.
  * @param url (String): The URL of the Git repository to clone.
  * @param workspace (String): The path to the Jenkins workspace where the repository should be cloned.
  * 
  * This function will clone the Git repository from the specified URL into the Jenkins workspace.
  */
  def clone(def pipeline, def args, String url, String workspace, Boolean shallowClone) {
    if (args['GITHUB_DISABLE_SSL_VERIFY']) {
      disableSslVerify(pipeline)
    }
    def hostName = pipeline.sh(returnStdout: true, script: "uname -n").trim()
    def gitClone = 'GITHUB_TOKEN=' + pipeline.env.GITHUB_TOKEN
    gitClone += ' git clone '
    if (shallowClone) {
      gitClone += 'depth -1 '
    }
    gitClone += url + ' "' + workspace + '"'
    pipeline.sh(gitClone)
    pipeline.echo("Cloned ${url} in ${workspace} on ${hostName}")
    return
  }

  /**
   * Execute Git Checkout
   * 
   * This function executes the `git checkout` command within a specified workspace to switch to a different branch.
   * 
   * @param pipeline (def): The Jenkins pipeline context.
   * @param branch (String): The name of the branch to check out.
   * @param workspace (String): The path to the Jenkins workspace where the Git repository is located.
   * 
   * This function will check out the specified branch within the provided workspace.
   */
  def execute(def pipeline, def branch, String workspace) {
    def checkoutBranch = 'cd "' + workspace + '" && git checkout ' + branch
    pipeline.echo("Checked out branch '$branch'")
    pipeline.sh(checkoutBranch)
  }

  /**
   * Get Default Git Branch
   * 
   * This function retrieves the default Git branch of a repository from its URL.
   * 
   * @param pipeline (def): The Jenkins pipeline context.
   * @param url (String): The URL of the Git repository.
   * @param workspace (String): The path to the Jenkins workspace where the Git repository is located.
   * @return: The name of the default branch.
   */
  def getDefaultBranch(def pipeline, def url, String workspace) {
    def cmdGetDefaultBranch = 'cd "' + workspace + '" &&'
    cmdGetDefaultBranch += ' GITHUB_TOKEN=' + pipeline.env.GITHUB_TOKEN
    cmdGetDefaultBranch += ' git remote show ' + url 
    cmdGetDefaultBranch += "  | grep 'HEAD branch' | cut -d' ' -f5"
    def defaultBranch = pipeline.sh(returnStdout: true, script: cmdGetDefaultBranch).trim()
    return defaultBranch
  }

  /**
  * Set Git Credential Helper
  * 
  * This function configures a Git credential helper to provide credentials automatically.
  * 
  * @param pipeline (def): The Jenkins pipeline context.
  */
  def setCredentialHelper(def pipeline) {
    def cmdGitCredentialHelper = "git config --global credential.helper"
    cmdGitCredentialHelper += " '!f() { sleep 1;"
    cmdGitCredentialHelper += ' echo "username=xxxx}";'
    cmdGitCredentialHelper += ' echo "password=${GITHUB_TOKEN}";'
    cmdGitCredentialHelper += " }; f'"
    pipeline.sh(cmdGitCredentialHelper)
    return
  }

  /**
  * Disable Git SSL Verification
  * 
  * This function disables SSL verification for Git, allowing connections to servers without SSL certificates.
  * 
  * @param pipeline (def): The Jenkins pipeline context.
  */
  def disableSslVerify(def pipeline) {
    def cmd = "git config --global http.sslVerify false"
    pipeline.sh(cmd)
    return
  }

  /**
  * Get Workspace Path
  * 
  * This function generates a workspace path based on the provided Git project URL.
  * If the URL matches the expected format, it creates a specific workspace directory; otherwise, it uses the default workspace.
  * 
  * @param pipeline (def): The Jenkins pipeline context.
  * @param project (String): The Git project URL.
  * @return: The workspace path.
  */
  def getWorkSpace(def pipeline, String project) {
    def matcher = project =~ /^https:\/\/([a-zA-Z\-\.]+)\/(?<org>[a-zA-Z\-_]+)\/(?<repo>[a-zA-Z\-_]+)\.git$/
    if (matcher.matches()) {
      String workspace = pipeline.env.WORKSPACE + "/${matcher.group('org')}_${matcher.group('repo')}"
      return workspace
    } else {
      return pipeline.env.WORKSPACE
    }
  }
}
