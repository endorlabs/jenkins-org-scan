package com.endorlabs
import groovy.json.JsonSlurper

class checkout implements Serializable {
  def call(def pipeline, String url) {
    return https(pipeline, url)
  }

  def https(def pipeline, String url) {
    def cmdGitCredentialHelper = "git config --global credential.helper"
    cmdGitCredentialHelper += " '!f() { sleep 1;"
    cmdGitCredentialHelper += ' echo "username=xxxx}";'
    cmdGitCredentialHelper += ' echo "password=${GITHUB_TOKEN}";'
    cmdGitCredentialHelper += " }; f'"
    pipeline.sh(cmdGitCredentialHelper)
    def gitClone = "GITHUB_TOKEN=" + pipeline.env.GITHUB_TOKEN
    gitClone += " git clone " + url + " ."
    pipeline.sh(gitClone)
    pipeline.sh("ls -la")
    def hostName = pipeline.sh(returnStdout: true, script: "uname -n").trim()
    def path = pipeline.sh(returnStdout: true, script: "pwd").trim()
    pipeline.echo("Cloned $url in $path on $hostName")
  }

  def git(def pipeline, String url, String credential) {
    def repo = repoName(url)
    if (! repo) {
      pipeline.currentBuild.result = 'FAILURE'
      pipeline.echo "Failed to identify org/repo for ${url}"
      pipeline.error "Failed to fetch repo ${url}"
    }
    def curlCmd = "curl -s -L"
    curlCmd += " -H 'Accept: application/vnd.github+json'"
    curlCmd += " -H 'Authorization: Bearer " + pipeline.env.GITHUB_TOKEN + "'"
    curlCmd += " -H 'X-GitHub-Api-Version: 2022-11-28'"
    curlCmd += " https://api.github.com/repos/" + repo
    def jsonTxt = pipeline.sh(returnStdout: true, script: curlCmd).trim()
    def jsonSlurper = new JsonSlurper()
    def data = jsonSlurper.parseText(jsonTxt)
    def branch = data.default_branch

    pipeline.checkout(
      scm: [
        $class: 'GitSCM',
        branches: [[name: branch]],
        userRemoteConfigs: [[
          credentialsId: credential,
          url: url
        ]]
      ]
    )
  }

  def repoName(String url) {
    def matcher = url =~ /^https:\/\/([a-zA-Z\-\.]+)\/(?<proj>[a-zA-Z\-_]+\/[a-zA-Z\-_]+)\.git$/
    if (matcher.matches()) {
        return matcher.group("proj")
    } else {
        return false
    }
  }
}