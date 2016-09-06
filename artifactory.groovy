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
    if (properties instanceof String) {
        props = properties
    } else {
        props = []
        for (e in properties) {
            props.push("${e.key}=${e.value}")
        }
        props = props.join('|')
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

/**
 * Push docker image and set artifact properties
 */
def dockerPush(artifactoryUrl, artifactoryOutRepo, dockerUrl, img, imgName, properties, timestamp, latest = true) {
    docker.withRegistry(dockerUrl, "artifactory") {
        img.push()
        // Also mark latest image
        img.push("latest")
    }

    properties["build.number"] = currentBuild.build().environment.BUILD_NUMBER
    properties["build.name"] = currentBuild.build().environment.JOB_NAME
    properties["timestamp"] = timestamp

    /* Set artifact properties */
    setProperty(
        artifactoryUrl,
        artifactoryOutRepo,
        imgName,
        timestamp,
        properties
    )

    // ..and the same for latest
    if (latest == true) {
        setProperty(
            artifactoryUrl,
            artifactoryOutRepo,
            imgName,
            "latest",
            properties
        )
    }
}

return this;
