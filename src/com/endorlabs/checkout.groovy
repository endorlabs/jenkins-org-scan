package com.endorlabs
import groovy.json.JsonSlurper

class checkout implements Serializable {
  def call(def pipeline, String url) {
    return https(pipeline, url)
  }

  def clone(def pipeline, String url) {
    def hostName = pipeline.sh(returnStdout: true, script: "uname -n").trim()
    def path = pipeline.sh(returnStdout: true, script: "pwd").trim()
    def gitClone = "GITHUB_TOKEN=" + pipeline.env.GITHUB_TOKEN
    gitClone += " git clone " + url + " ."
    pipeline.sh(gitClone)
    pipeline.echo("Cloned $url in $path on $hostName")
    return
  }

  def execute(def pipeline, def branch) {
    def checkoutBranch = "git checkout " + branch
    pipeline.echo("Checked out branch '$branch'")
    pipeline.sh(checkoutBranch)
  }

  def getDefaultBranch(def pipeline, def url) {
    def cmdGetDefaultBranch = "GITHUB_TOKEN=" + pipeline.env.GITHUB_TOKEN
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
}