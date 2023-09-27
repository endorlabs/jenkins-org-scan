/**
 * Pull Endor Docker Image
 * 
 * This function is used to pull the Endor Docker image with a specified tag from a container registry.
 * The image URL is constructed based on the provided tag, and a Docker pull command is executed
 * to fetch the image.
 * 
 * @param tag (String): The tag of the Endor Docker image to pull.
 * 
 * @throws error: Throws an error and terminates the pipeline if the Docker image pull fails.
 * 
 * Example Usage:
 * String dockerImageTag = 'v1.0.0'
 * pullEndorDockerImage(dockerImageTag)
 * 
 * This function will attempt to pull the Endor Docker image with the specified tag.
 * If the pull operation fails, an error is thrown, and the pipeline is terminated.
 */

def pullEndorDockerImage(String tag) {
  def image = "us-central1-docker.pkg.dev/endor-ci/public/endorctl"
  def command = "docker pull " + image + ":" + tag
  sh command
}
