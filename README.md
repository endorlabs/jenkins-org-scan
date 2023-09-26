# Jenkins Pipeline for endorctl scan

## Steps

The Jenkins Pipeline performs the following steps:
- Pull Docker Image
- Runs `sync-org` command to sync the projects (github repositories) to the projects list in the Tenant's **namespace**.
- Retrieves the project list (github repositories) for the given Tenant's namespace.
- Groups the projects in batches (as per configuration)
- Run `endorctl` scan on each group of projects in parallel.

## Using the Pipeline
The Jenkins Pipeline ([github-org-scan-docker.groovy](https://github.com/endorlabs/jenkins-org-scan/blob/main/github-org-scan-docker.groovy)) is available at <https://github.com/endorlabs/jenkins-org-scan.git>.
The pipeline script does not have any parameter configured but it requires a few parameters to be configured depending on the scenario. It was possible to include the parameters in the code iteself, but as end users may require the default value of the  parameters to be customised as per their requirements, we decided to have the parameters configured manually from Jenkins UI.

### Secrets
The pipeline scripts assumes that 3 secrets are available and stored with the following names:

- `GITHUB_TOKEN` --> This secret should have the access token with permission to access all the repositories in the given organisation.
- `ENDOR_LABS_API_KEY` --> This secret should contain the API Key to access the Endor Labs Environment. To create this Key and corresponding secret, 
  - Navigate to **Access Control** (under Manage) --> **API KEYS** --> **Generate API Key**
  - Provide a name to quickly identify the Key for `NAME YOUR API KEY`
  - For `PERMISSIONS` select `Code Scanner`
  - For `WHEN SHOULD YOUR API KEY EXPIRE?`, select the validity of the generated key.
- `ENDOR_LABS_API_SECRET` --> This secret should contain the API Secret generated which creating the API Key.

### Configuration for Github Cloud
#### Required Parameters

- `AGENT_LABEL` --> This is a *String Parameter*. It denotes the Label to be used to identify the Jenkins Agents. The Jenkins Job would run on the agent(s) with the given label.
- `GITHUB_ORG` --> The Github Organisation Name of the Customer.
- `ENDOR_LABS_NAMESPACE` --> The Tenant **namespace** of the customer.

#### Optional Parameters
- `ENDOR_LABS_API` --> This is only required if the Tenant **namespace** is configured on our Staging Environment.
- `ADDITIONAL_ARGS` --> (*String Parameter*) Use this field to pass any additional parameter to `endorctl` scan
- `NO_OF_THREADS` --> This denotes the number of Jenkins Agent that can be used in parallel for the `endorctl` scan. If a customer has 10 Jenkins Agent configured with the given `AGENT_LABEL`, this value should be 9 (we use 1 agent for the main job). This values defaults to **5** if not specified.

### Configuration for Github Enterprise Server
#### Required Parameters

- `AGENT_LABEL` --> (*String Parameter*) It denotes the Label to be used to identify the Jenkins Agents. The Jenkins Job would run on the agent(s) with the given label.
- `GITHUB_ORG` --> (*String Parameter*) It denotes the Github Organisation Name of the Customer.
- `ENDOR_LABS_NAMESPACE` --> (*String Parameter*) The Tenant **namespace** of the customer.
- `GITHUB_API_URL` --> (*String Parameter*) The API URL of the Github Enterprise Server. This is normally in the for `<FQDN of Github Enterprise Server>/api/v3` e.g., <https://ghe.endorlabs.in/api/v3>

#### Optional Parameters

- `ENDOR_LABS_API` --> (*String Parameter*) This is only required if the Tenant **namespace** is configured on our Staging Environment.
- `GITHUB_DISABLE_SSL_VERIFY` --> (*Boolean Parameter*) This should be used when you want to skip SSL Verification while cloning the repository.
- `GITHUB_CA_CERT` --> (*Multi-line String Parameter*) This should be used to provide the content of CA Certificate (PEM format) of the SSL Certificate used on Github Enterprise Server.
- `PROJECT_LIST` --> (*Multi-line String Parameter*) This should be used to provide a list of projects to scan. 
**Note**: If proper SSL Certificate (i.e., Certificate issued by wellknown CA) is not used for Github Enterprise, the `sync-org` command would fail and won't be able to fetch the projects/repositories to scan from the Github Enterprise Server. Therefor, use this field to provide the list of projects/repositores to scan one per line. e.g.,
```
https://github-test.endorlabs.in/pse/vuln_rust_callgraph.git
https://github-test.endorlabs.in/pse/vulnerable-golang.git
https://github-test.endorlabs.in/pse/java-javascript-vulnerable-repo.git
https://github-test.endorlabs.in/pse/multi-lang-repo.git
```

- `ADDITIONAL_ARGS` --> (*String Parameter*) Use this field to pass any additional parameter to `endorctl` scan
- `NO_OF_THREADS` --> (*String Parameter*) This denotes the number of Jenkins Agent that can be used in parallel for the `endorctl` scan. If a customer has 10 Jenkins Agent configured with the given `AGENT_LABEL`, this value should be 9 (we use 1 agent for the main job). This values defaults to **5** if not specified.


### Steps to configure the Job
1. Login to Jenkins
2. Make sure the above mentioned secrets are configured correctly for your environment.
3. Click on `+ New Item`, to create a new Jenkins Job
4. Enter the name of the new pipeline
5. Select `Pipeline` and Click "OK" button
6. In the next page, select `This project is parameterised`.
7. Add the parameters depending on the requirement as mentioned above
8. Come down to the **Pipeline** section
9. For `Definition` seclect *Pipeline script from SCM*
10. For `SCM` select *Git*
11. For `Repository URL`, enter either `git@github.com:endorlabs/jenkins-org-scan.git` or `https://github.com/endorlabs/jenkins-org-scan.git`
12. For Credentials, provide the secrets as required for cloning the Repository entered in Steps 11
13. For `Branches to build`, enter `*/main`.
14. For `Script Path`, enter `github-org-scan-docker.groovy`
15. Select `Lightweight checkout`
16. Click `Save` button
