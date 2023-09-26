package com.endorlabs
import groovy.json.JsonSlurper

class checkout implements Serializable {
  def call(def pipeline, String url) {
    return https(pipeline, url)
  }

  def clone(def pipeline, def args, String url, String workspace) {
    if (args['GITHUB_DISABLE_SSL_VERIFY']) {
      disableSslVerify(pipeline)
    }
    def hostName = pipeline.sh(returnStdout: true, script: "uname -n").trim()
    def path = pipeline.sh(returnStdout: true, script: "pwd").trim()
    def gitClone = "GITHUB_TOKEN=" + pipeline.env.GITHUB_TOKEN
    gitClone += " git clone " + url + " " + workspace
    pipeline.sh(gitClone)
    pipeline.echo("Cloned ${url} in ${path}/${workspace} on ${hostName}")
    return
  }

  def execute(def pipeline, def branch, String workspace) {
    def checkoutBranch = "cd " + workspace + " && git checkout " + branch
    pipeline.echo("Checked out branch '$branch'")
    pipeline.sh(checkoutBranch)
  }

  def getDefaultBranch(def pipeline, def url, String workspace) {
    def cmdGetDefaultBranch = "cd " + workspace + " &&"
    cmdGetDefaultBranch += " GITHUB_TOKEN=" + pipeline.env.GITHUB_TOKEN
    cmdGetDefaultBranch += " git remote show " + url 
    cmdGetDefaultBranch += "  | grep 'HEAD branch' | cut -d' ' -f5"
    def defaultBranch = pipeline.sh(returnStdout: true, script: cmdGetDefaultBranch).trim()
    return defaultBranch
  }

  def setCredentialHelper(def pipeline) {
    def cmdGitCredentialHelper = "git config --global credential.helper"
    cmdGitCredentialHelper += " '!f() { sleep 1;"
    cmdGitCredentialHelper += ' echo "username=xxxx}";'
    cmdGitCredentialHelper += ' echo "password=${GITHUB_TOKEN}";'
    cmdGitCredentialHelper += " }; f'"
    pipeline.sh(cmdGitCredentialHelper)
    return
  }

  def disableSslVerify(def pipeline) {
    def cmd = "git config --global http.sslVerify false"
    pipeline.sh(cmd)
    return
  }

  def getWorkSpace(def pipeline, String project) {
    def matcher = project =~ /^https:\/\/([a-zA-Z\-\.]+)\/(?<org>[a-zA-Z\-_]+)\/(?<repo>[a-zA-Z\-_]+)\.git$/
    if (matcher.matches()) {
      workspace = pipeline.env.WORKSPACE + "/${matcher.group('org')}_${matcher.group('repo')}"
      return workspace
    } else {
      return pipeline.env.WORKSPACE
    }
  }
}
