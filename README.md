# Jenkins Pipeline for Endorctl Scan

## Overview

This Jenkins Pipeline automates the process of running `endorctl` scans on GitHub repositories. It is designed to work in both GitHub Cloud and GitHub Enterprise Server environments. The pipeline follows these main steps:

1. Pulls a Docker image required for the scans.
2. Synchronizes GitHub organization repositories to a specified namespace in the Endor Labs platform.
3. Retrieves the list of projects (GitHub repositories) from the specified namespace.
4. Groups the projects into batches to optimize scan execution.
5. Runs `endorctl` scans on each batch of projects in parallel.

## Usage

### Pipeline Script

The Jenkins Pipeline script is available in the [github-org-scan-docker.groovy](https://github.com/endorlabs/jenkins-org-scan/blob/main/github-org-scan-docker.groovy) file.

### Configuration Parameters

#### Secrets

Before using the pipeline, ensure that the following secrets are correctly configured in your Jenkins environment:

- `GITHUB_TOKEN`: GitHub access token.
- `ENDOR_LABS_API_KEY`: Endor Labs API Key.
- `ENDOR_LABS_API_SECRET`: Endor Labs API Secret.

#### Configuration Parameters (GitHub Cloud)

**Required Parameters:**

- `AGENT_LABEL` (String Parameter): Label of the Jenkins Agent where the job will be executed.
- `GITHUB_ORG` (String Parameter): Your GitHub organization name.
- `ENDOR_LABS_NAMESPACE` (String Parameter): Endor Labs platform namespace for scan results.

**Optional Parameters:**

- `ENDORCTL_VERSION` (String Parameter): Specify the version of the `endorctl` Docker container. Defaults to the latest version.
- `ENDOR_LABS_API` (String Parameter): Required if the Tenant namespace is configured on the Staging Environment.
- `ENABLE_SCAN` (String Parameter): Set to 'git' to scan commits and/or 'github' to fetch info from the GitHub API. Default is ['git', 'analytics'].
- `SCAN_SUMMARY_OUTPUT_TYPE` (String Parameter): Set the desired output format. Supported formats: 'json', 'yaml', 'table', 'summary'. Default is "table".
- `LOG_LEVEL` (String Parameter): Sets the log level of the application. Default is "info".
- `LOG_VERBOSE` (String Parameter): Makes the log verbose.
- `LANGUAGES` (String Parameter): Set programming languages to scan. Supported languages: c#, go, java, javascript, php, python, ruby, rust, scala, typescript. Default is a list of supported languages.
- `ADDITIONAL_ARGS` (String Parameter): Use this field to pass any additional parameters to the `endorctl` scan.
- `NO_OF_THREADS` (String Parameter): Number of Jenkins Agents that can be used in parallel for the `endorctl` scan. Defaults to **5** if not specified.
- `PROJECT_LIST` (Multi-line String Parameter): List of projects to scan. Even though all projects are synchronized, scans run only on the provided projects.
- `EXCLUDE_PROJECTS` (Multi-line String Parameter): List of projects/repositories to exclude from scan.

#### Configuration Parameters (GitHub Enterprise Server)

**Required Parameters:**

- `AGENT_LABEL` (String Parameter): Label to identify the Jenkins Agents where the job will run.
- `GITHUB_ORG` (String Parameter): Your GitHub organization name.
- `ENDOR_LABS_NAMESPACE` (String Parameter): Endor Labs platform namespace for scan results.
- `GITHUB_API_URL` (String Parameter): API URL of the GitHub Enterprise Server, e.g., `https://ghe.example.com/api/v3`.

**Optional Parameters:**

- `ENDORCTL_VERSION` (String Parameter): Specify the version of the `endorctl` Docker container. Defaults to the latest version.
- `ENDOR_LABS_API` (String Parameter): Required if the Tenant namespace is configured on the Staging Environment.
- `ENABLE_SCAN` (String Parameter): Set to 'git' to scan commits and/or 'github' to fetch info from the GitHub API. Default is ['git', 'analytics'].
- `SCAN_SUMMARY_OUTPUT_TYPE` (String Parameter): Set the desired output format. Supported formats: 'json', 'yaml', 'table', 'summary'. Default is "table".
- `LOG_LEVEL` (String Parameter): Sets the log level of the application. Default is "info".
- `LOG_VERBOSE` (String Parameter): Makes the log verbose.
- `LANGUAGES` (String Parameter): Set programming languages to scan. Supported languages: c#, go, java, javascript, php, python, ruby, rust, scala, typescript. Default is a list of supported languages.
- `ADDITIONAL_ARGS` (String Parameter): Use this field to pass any additional parameters to the `endorctl` scan.
- `NO_OF_THREADS` (String Parameter): Number of Jenkins Agents that can be used in parallel for the `endorctl` scan. Defaults to **5** if not specified.
- `EXCLUDE_PROJECTS` (Multi-line String Parameter): List of projects/repositories to exclude from scan.
- `GITHUB_DISABLE_SSL_VERIFY` (Boolean Parameter): Use when you want to skip SSL Verification while cloning the repository.
- `GITHUB_CA_CERT` (Multi-line String Parameter): Provide the content of CA Certificate (PEM format) of the SSL Certificate used on GitHub Enterprise Server.
- `PROJECT_LIST` (Multi-line String Parameter): List of projects to scan.

### Steps to Configure the Job

1. Log in to Jenkins.
2. Ensure the required secrets are configured correctly.
3. Create a new Jenkins job.
4. Configure the job by specifying parameters and other settings.
5. Use the provided Git repository URL and script path.
6. Save the job configuration.

This Jenkins Pipeline is highly customizable and adaptable to various GitHub environments and scanning requirements. It streamlines the process of running `endorctl` scans on your repositories efficiently.

If you have any specific questions or need further assistance with any part of this pipeline, please feel free to ask.
