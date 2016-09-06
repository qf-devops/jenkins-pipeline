def uploadArtifact(repo, file) {
    def artifactory = Artifactory.server(System.getenv("ARTIFACTORY_SERVERID") ?: "default")
    def uploadSpec = """{
    "files": [{
        "pattern": "${file}",
        "target": "${repo}/${file}"
    }]
    }"""

    artifactory.upload(uploadSpec)
}

def downloadArtifact(repo, file) {
    def artifactory = Artifactory.server(System.getenv("ARTIFACTORY_SERVERID") ?: "default")
    def downloadSpec = """{
    "files": [{
        "pattern": "${file}",
        "target": "${repo}/${file}"
    }]
    }"""

    artifactory.download(downloadSpec)
}

/**
 * Set single property or list of properties to existing artifact
 *
 * @param repo      Local repository name
 * @param name      Name of artifact
 * @param version   Artifact's version, eg. Docker image tag
 * @param properties    String or list of properties in key=value format
 * @param recursive Set properties recursively (default false)
 */
def setProperty(artifactoryUrl, repo, name, version, properties, recursive = 0) {
    c = getCredentials('artifactory');
    if (properties instanceof List) {
        props = properties.join('|');
    } else {
        props = properties
    }
    sh "curl -s -f -u ${c.username}:${c.password} -X PUT '${artifactoryUrl}/api/storage/${repo}/${name}/${version}?properties=${props}&recursive=${recursive}'"
}

/**
 * Get credentials from store
 *
 * @param id    Credentials name
 */
def getCredentials(id) {
    def creds = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
                    com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials.class,
                    jenkins.model.Jenkins.instance
                )

    for (Iterator<String> credsIter = creds.iterator(); credsIter.hasNext();) {
        c = credsIter.next();
        if ( c.id == id ) {
            return c;
        }
    }

    throw new Exception("Could not find credentials for ID ${id}")
}

return this;
