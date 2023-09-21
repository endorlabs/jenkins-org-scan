package com.endorlabs
import groovy.json.JsonSlurper

class checkout implements Serializable {
  def call(def pipeline, String url) {
    return https(pipeline, url)
  }

  def https(def pipeline, String url, String branch) {
    def gitClone = "GITHUB_TOKEN=" + pipeline.env.GITHUB_TOKEN
    gitClone += " git clone " + url + " ."
    pipeline.sh(gitClone)
    def checkoutDefaultBranch = "git checkout " + branch
    pipeline.sh(checkoutDefaultBranch)
    def hostName = pipeline.sh(returnStdout: true, script: "uname -n").trim()
    def path = pipeline.sh(returnStdout: true, script: "pwd").trim()
    pipeline.echo("Cloned $url , Branch: $branch in $path on $hostName")
    return defaultBranch
  }

  def getDefaultBranch(def pipeline, def parameters) {
    def cmdGetDefaultBranch = "GITHUB_TOKEN=" + parameters['GITHUB_TOKEN']
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
  }
}