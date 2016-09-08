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
 * Make generic call using Artifactory REST API and return parsed JSON
 *
 * @param art   Artifactory connection object
 * @param uri   URI which will be appended to artifactory server base URL
 * @param method    HTTP method to use (default GET)
 * @param data      JSON data to POST or PUT
 * @param headers   Map of additional request headers
 */
def restCall(art, uri, method = 'GET', data = null, headers = []) {
    def connection = new URL("${art.url}/api${uri}").openConnection()
    if (method != 'GET') {
        connection.setRequestMethod(method)
    }

    connection.setRequestProperty('User-Agent', 'jenkins-groovy')
    connection.setRequestProperty('Accept', 'application/json')
    connection.setRequestProperty('Authorization', "Basic " +
        "${art.creds.username}:${art.creds.password}".bytes.encodeBase64().toString())

    for (header in headers) {
        connection.setRequestProperty(header.key, header.value)
    }

    if (data) {
        connection.setRequestProperty('Content-Type', 'application/json')
        connection.setDoOutput(true)
        if (data instanceof String) {
            dataStr = data
        } else {
            dataStr = new groovy.json.JsonBuilder(data).toString()
        }
        def out = new OutputStreamWriter(connection.outputStream)
        out.write(dataStr)
        out.close()
    }

    if ( connection.responseCode == 200 ) {
        res = connection.inputStream.text
        try {
            return new groovy.json.JsonSlurperClassic().parseText(res)
        } catch (groovy.json.JsonException e) {
            return res
        }
    } else {
        throw new Exception(connection.responseCode + ": " + connection.inputStream.text)
    }
}

/**
 * Make GET request using Artifactory REST API and return parsed JSON
 *
 * @param art   Artifactory connection object
 * @param uri   URI which will be appended to artifactory server base URL
 */
def restGet(art, uri) {
    return restCall(art, uri)
}

/**
 * Make PUT request using Artifactory REST API and return parsed JSON
 *
 * @param art   Artifactory connection object
 * @param uri   URI which will be appended to artifactory server base URL
 * @param data  JSON Data to PUT
 */
def restPut(art, uri, data = null) {
    return restCall(art, uri, 'PUT', data, ['Accept': '*/*'])
}

/**
 * Make POST request using Artifactory REST API and return parsed JSON
 *
 * @param art   Artifactory connection object
 * @param uri   URI which will be appended to artifactory server base URL
 * @param data  JSON Data to PUT
 */
def restPost(art, uri, data = null) {
    return restCall(art, uri, 'POST', data, ['Accept': '*/*'])
}

/**
 * Query artifacts by properties
 *
 * @param art   Artifactory connection object
 * @param properties    String or list of properties in key=value format
 * @param repo  Optional repository to search in
 */
def findArtifactByProperties(art, properties, repo) {
    query = parseProperties(properties)
    if (repo) {
        query = query + "&repos=${repo}"
    }
    res = restGet(art, "/search/prop?${query}")
    return res.results
}

/**
 * Parse properties string or map and return URL-encoded string
 *
 * @param properties    string or key,value map
 */
def parseProperties(properties) {
    if (properties instanceof String) {
        return properties
    } else {
        props = []
        for (e in properties) {
            props.push("${e.key}=${e.value}")
        }
        props = props.join('|')
        return props
    }
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
    props = parseProperties(properties)
    sh "curl -s -f -u ${art.creds.username}:${art.creds.password} -X PUT '${art.url}/api/storage/${art.outRepo}/${name}/${version}?properties=${props}&recursive=${recursive}'"
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
def connection(url, serverId, dockerRegistryUrl, outRepo, credentialsId = "artifactory") {
    params = [
        "url": url,
        "serverId": serverId,
        "credentialsId": credentialsId,
        "dockerRegistryUrl": dockerRegistryUrl,
        "outRepo": outRepo,
        "creds": getCredentials(credentialsId)
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

/**
 * Set offline parameter to repositories
 *
 * @param art       Artifactory connection object
 * @param repos     List of base repositories
 * @param suffix    Suffix to append to new repository names
 */
def setOffline(art, repos, suffix) {
    for (repo in repos) {
        repoName = "${repo}-${suffix}"
        restPost(art, "/repositories/${repoName}", ['offline': true])
    }
    return
}

/**
 * Create repositories based on timestamp or other suffix from already
 * existing repository
 *
 * @param art       Artifactory connection object
 * @param repos     List of base repositories
 * @param suffix    Suffix to append to new repository names
 */
def createRepos(art, repos, suffix) {
    def created = []
    for (repo in repos) {
        repoNewName = "${repo}-${suffix}"
        repoOrig = restGet(art, "/repositories/${repo}")
        repoOrig.key = repoNewName
        repoNew  = restPut(art, "/repositories/${repoNewName}", repoOrig)
        created.push(repoNewName)
    }
    return created
}

return this;
