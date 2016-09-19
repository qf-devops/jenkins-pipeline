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
 * Make generic call using Salt REST API and return parsed JSON
 *
 * @param master   Salt connection object
 * @param uri   URI which will be appended to Salt server base URL
 * @param method    HTTP method to use (default GET)
 * @param data      JSON data to POST or PUT
 * @param headers   Map of additional request headers
 */
def restCall(master, uri, method = 'GET', data = null, headers = [:]) {
    def connection = new URL("${master.url}${uri}").openConnection()
    if (method != 'GET') {
        connection.setRequestMethod(method)
    }

    connection.setRequestProperty('User-Agent', 'jenkins-groovy')
    connection.setRequestProperty('Accept', 'application/json')
    if (master.authToken) {
        // XXX: removeme
        connection.setRequestProperty('X-Auth-Token', master.authToken)
    }

    for (header in headers) {
        connection.setRequestProperty(header.key, header.value)
    }

    if (data) {
        connection.setDoOutput(true)
        if (data instanceof String) {
            dataStr = data
        } else {
            connection.setRequestProperty('Content-Type', 'application/json')
            dataStr = new groovy.json.JsonBuilder(data).toString()
        }
        def out = new OutputStreamWriter(connection.outputStream)
        out.write(dataStr)
        out.close()
    }

    if ( connection.responseCode >= 200 && connection.responseCode < 300 ) {
        res = connection.inputStream.text
        try {
            return new groovy.json.JsonSlurperClassic().parseText(res)
        } catch (Exception e) {
            return res
        }
    } else {
        throw new Exception(connection.responseCode + ": " + connection.inputStream.text)
    }
}

/**
 * Make GET request using Salt REST API and return parsed JSON
 *
 * @param master   Salt connection object
 * @param uri   URI which will be appended to Salt server base URL
 */
def restGet(master, uri, data = null) {
    return restCall(master, uri, 'GET', data)
}

/**
 * Make POST request using Salt REST API and return parsed JSON
 *
 * @param master   Salt connection object
 * @param uri   URI which will be appended to Docker server base URL
 * @param data  JSON Data to PUT
 */
def restPost(master, uri, data = null) {
    return restCall(master, uri, 'POST', data, ['Accept': '*/*'])
}

/**
 * Salt connection and context parameters
 *
 * @param url                 Salt API server URL
 * @param credentialsID       ID of credentials store entry
 */
def connection(url, credentialsId = "salt") {
    params = [
        "url": url,
        "credentialsId": credentialsId,
        "authToken": null,
        "creds": getCredentials(credentialsId)
    ]
    params["authToken"] = saltLogin(params)

    return params
}

/**
 * Login to Salt API, return auth token
 *
 * @param master   Salt connection object
 */
def saltLogin(master) {
    data = [
        'username': master.creds.username,
        'password': master.creds.password.toString(),
        'eauth': 'pam'
    ]
    authToken = restGet(master, '/login', data)['return'][0]['token']
    return authToken
}

/**
 * Run action using Salt API
 *
 * @param master   Salt connection object
 * @param client   Client type
 * @param target   Target specification, eg. for compound matches by Pillar
 *                 data: ['expression': 'I@openssh:server', 'type': 'compound'])
 * @param function Function to execute (eg. "state.sls")
 * @param args     Additional arguments to function
 * @param kwargs     Additional key-value arguments to function
 */
def runCommand(master, client, target, function, args = null, kwargs = null) {
    data = [
        'tgt': target.expression,
        'fun': function,
        'client': client,
        'expr_form': target.type,
    ]

    if (args) {
        data['arg'] = args
    }
    if (kwargs) {
        data['kwarg'] = kwargs
    }

    return restPost(master, '/', [data])
}

def enforceState(master, target, state, output = false) {
    def run_states
    if (state instanceof String) {
        run_states = state
    } else {
        run_states = state.join(',')
    }

    def out = runCommand(master, 'local', target, 'state.sls', [run_states])
    try {
        checkResult(out)
    } finally {
        if (output == true) {
            printResult(out)
        }
    }
    return out
}

def cmdRun(master, target, cmd) {
    def out = runCommand(master, 'local', target, 'cmd.run', [cmd])
    return out
}

def syncAll(master, target) {
    return runCommand(master, 'local', target, 'saltutil.sync_all')
}

def enforceHighstate(master, target, output = false) {
    def out = runCommand(master, 'local', target, 'state.highstate')
    try {
        checkResult(out)
    } finally {
        if (output == true) {
            printResult(out)
        }
    }
    return out
}

def generateNodeKey(master, target, host, keysize = 4096) {
    args = [host]
    kwargs = ['keysize': keysize]
    return runCommand(master, 'wheel', target, 'key.gen_accept', args, kwargs)
}

def generateNodeMetadata(master, target, host, classes, parameters) {
    args = [host, '_generated']
    kwargs = ['classes': classes, 'parameters': parameters]
    return runCommand(master, 'local', target, 'reclass.node_create', args, kwargs)
}

def orchestrateSystem(master, target, orchestrate) {
    return runCommand(master, 'runner', target, 'state.orchestrate', [orchestrate])
}

/**
 * Check result for errors and throw exception if any found
 *
 * @param result    Parsed response of Salt API
 */
def checkResult(result) {
    for (entry in result['return']) {
        if (!entry) {
            throw new Exception("Salt API returned empty response: ${result}")
        }
        for (node in entry) {
            for (resource in node.value) {
                if (resource instanceof String || resource.value.result.toString().toBoolean() != true) {
                    throw new Exception("Salt state on node ${node.key} failed: ${node.value}")
                }
            }
        }
    }
}

/**
 * Print possibly user-friendly result of Salt run
 *
 * @param result    Parsed response of Salt API
 * @param onlyChanges   If true (default), print only changed resources
 * @param raw           Simply pretty print what we have, no additional
 *                      parsing
 */
def printResult(result, onlyChanges = true, raw = false) {
    if (raw == true) {
        print new groovy.json.JsonBuilder(result).toPrettyString()
    } else {
        def out = [:]
        for (entry in result['return']) {
            for (node in entry) {
                out[node.key] = [:]
                for (resource in node.value) {
                    if (resource instanceof String) {
                        out[node.key] = node.value
                    } else if (resource.value.result.toString().toBoolean() == false || resource.value.changes || onlyChanges == false) {
                        out[node.key][resource.key] = resource.value
                    }
                }
            }
        }

        for (node in out) {
            if (node.value) {
                println "Node ${node.key} changes:"
                print new groovy.json.JsonBuilder(node.value).toPrettyString()
            } else {
                println "No changes for node ${node.key}"
            }
        }
    }
}

return this;
