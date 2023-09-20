def pullEndorDockerImage(String tag) {
  def image = "us-central1-docker.pkg.dev/endor-ci/public/endorctl"
  def command = "docker pull " + image + ":" + tag
  sh command
}

def checkoutHTTPS(String url, String token) {
  checkout \
    changelog: false, 
    poll: false, 
    scm: scmGit(
      branches: [[name: '**']], 
      extensions: [
        cleanBeforeCheckout(
          deleteUntrackedNestedRepositories: true
        ), 
        cloneOption(
          depth: 1, 
          honorRefspec: true, 
          noTags: true, 
          reference: '', 
          shallow: true
        )
      ], 
      userRemoteConfigs: [[
        credentialsId: token, 
        url: url 
      ]]
    )
}
