package com.endorlabs
import groovy.json.JsonSlurper

class checkout implements Serializable {
  private String hostName
  private String path
  private String project
  private String branch

  def call(def pipeline, String url) {
    return https(pipeline, url)
  }

  def getHostName(def pipeline) {
    this.hostName = pipeline.sh(returnStdout: true, script: "uname -n").trim()
    return hostName
  }

  def getCurrentPath(def pipeline) {
    this.path = pipeline.sh(returnStdout: true, script: "pwd").trim()
    return path
  }

  
  def clone(def pipeline, String url) {
    def gitClone = "GITHUB_TOKEN=" + pipeline.env.GITHUB_TOKEN
    gitClone += " git clone " + url + " ."
    pipeline.sh(gitClone)
    pipeline.echo("Cloned $url in $path on $hostName")
    return defaultBranch
  }

  def checkout(def pipeline, def branch) {
    def checkoutBranch = "git checkout " + branch
    pipeline.echo("Checked out branch $branch of $project")
    pipeline.sh(checkoutBranch)
  }

  def getDefaultBranch(def pipeline, def url) {
    def cmdGetDefaultBranch = "GITHUB_TOKEN=" + pipeline.env.GITHUB_TOKEN
    cmdGetDefaultBranch += " git remote show " + url 
    cmdGetDefaultBranch += "  | grep 'HEAD branch' | cut -d' ' -f5"
    def defaultBranch = pipeline.sh(returnStdout: true, script: cmdGetDefaultBranch).trim()
    this.branch = defaultBranch
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
}