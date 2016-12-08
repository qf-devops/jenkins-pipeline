
// Load shared libs
common = evaluate(new File("${env.WORKSPACE}/libLoader/common.groovy"))
http = evaluate(new File("${env.WORKSPACE}/libLoader/http.groovy"))

/**
 * Login to Salt API and return auth token
 *
 * @param url            Salt API server URL
 * @param params         Salt connection params
 */
def getToken(url, params) {
    data = [
        'username': params.creds.username,
        'password': params.creds.password.toString(),
        'eauth': 'pam'
    ]
    authToken = http.sendGetRequest("${url}/login", data, ['Accept': '*/*'])['return'][0]['token']
    return authToken
}

/**
 * Salt connection and context parameters
 *
 * @param url            Salt API server URL
 * @param credentialsID  ID of credentials store entry
 */
def createConnection(url, credentialsId) {
    params = [
        "url": url,
        "credentialsId": credentialsId,
        "authToken": null,
        "creds": common.getPasswordCredentials(credentialsId)
    ]
    params["authToken"] = getToken(url, params)

    return params
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
 * @param kwargs   Additional key-value arguments to function
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

    return http.sendPostRequest(master, '/', [data])
}

def getPillar(master, target, pillar) {
    def out = runCommand(master, 'local', target, 'pillar.get', [pillar.replace('.', ':')])
    return out
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

def runCmd(master, target, cmd) {
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
