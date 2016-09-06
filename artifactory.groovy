def uploadArtifact(repo, file) {
    def artifactory = Artifactory.server(art.serverId ?: "default")
    def uploadSpec = """{
    "files": [{
        "pattern": "${file}",
        "target": "${repo}/${file}"
    }]
    }"""

    artifactory.upload(uploadSpec)
}

def downloadArtifact(art, repo, file) {
    def artifactory = Artifactory.server(art.serverId ?: "default")
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
 * @param art       Artifactory connection object
 * @param name      Name of artifact
 * @param version   Artifact's version, eg. Docker image tag
 * @param properties    String or list of properties in key=value format
 * @param recursive Set properties recursively (default false)
 */
def setProperty(art, name, version, properties, recursive = 0) {
    c = getCredentials(art.credentialsId);
    if (properties instanceof String) {
        props = properties
    } else {
        props = []
        for (e in properties) {
            props.push("${e.key}=${e.value}")
        }
        props = props.join('|')
    }
    sh "curl -s -f -u ${c.username}:${c.password} -X PUT '${art.url}/api/storage/${art.outRepo}/${name}/${version}?properties=${props}&recursive=${recursive}'"
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

/**
 * Artifactory connection and context parameters
 * Not all parameters are needed for all provided methods (some are using REST
 * API and require url while some are using Artifactory plugin and require
 * serverId)
 *
 * @param url       Artifactory server URL
 * @param serverId  Server ID of artifactory plugin
 * @param dockerRegistryUrl   URL to docker registry used in context of this
 *                            connection
 * @param outRepo             Output repository name used in context of this
 *                            connection
 * @param credentialsID       ID of credentials store entry
 */
def connection(url, serverId, dockerRegistryUrl, outRepo, credentialsID = "artifactory") {
    params = [
        "url": url,
        "serverId": serverId,
        "credentialsId": credentialsID,
        "dockerRegistryUrl": dockerRegistryUrl,
        "outRepo": outRepo
    ]
    return params
}

/**
 * Push docker image and set artifact properties
 *
 * @param art   Artifactory connection object
 * @param img   Docker image object
 * @param imgName       Name of docker image
 * @param properties    Map of additional artifact properties
 * @param timestamp     Build timestamp
 * @param latest        Push latest tag if set to true (default true)
 */
def dockerPush(art, img, imgName, properties, timestamp, latest = true) {
    docker.withRegistry(art.dockerRegistryUrl, art.credentialsId) {
        img.push()
        // Also mark latest image
        img.push("latest")
    }

    properties["build.number"] = currentBuild.build().environment.BUILD_NUMBER
    properties["build.name"] = currentBuild.build().environment.JOB_NAME
    properties["timestamp"] = timestamp

    /* Set artifact properties */
    setProperty(
        art,
        imgName,
        timestamp,
        properties
    )

    // ..and the same for latest
    if (latest == true) {
        setProperty(
            art,
            imgName,
            "latest",
            properties
        )
    }
}

return this;
