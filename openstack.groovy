
// Load shared libs
common = evaluate(new File("${env.WORKSPACE}/libLoader/common.groovy"))
python = evaluate(new File("${env.WORKSPACE}/libLoader/python.groovy"))

def openstack_kilo_packages = [
    'python-cinderclient>=1.3.1,<1.4.0',
    'python-glanceclient>=0.19.0,<0.20.0',
    'python-heatclient>=0.6.0,<0.7.0',
    'python-keystoneclient>=1.6.0,<1.7.0',
    'python-neutronclient>=2.2.6,<2.3.0',
    'python-novaclient>=2.19.0,<2.20.0',
    'python-swiftclient>=2.5.0,<2.6.0',
    'oslo.config>=2.2.0,<2.3.0',
    'oslo.i18n>=2.3.0,<2.4.0',
    'oslo.serialization>=1.8.0,<1.9.0',
    'oslo.utils>=1.4.0,<1.5.0',
]

def openstack_latest_packages = openstack_kilo_packages

/**
 * create connection to OpenStack API endpoint
 *
 * @param url             OpenStack API endpoint address
 * @param credentialsId   Credentials to the OpenStack API
 * @param project         OpenStack project to connect to
 */
def createEnvParams(url, credentialsId, project) {
    creds = common.getPasswordCredentials(credentialsId)
    params = [
        "OS_USERNAME": creds.username,
        "OS_PASSWORD": creds.password.toString(),
        "OS_TENANT_NAME": project,
        "OS_AUTH_URL": url,
        "OS_AUTH_STRATEGY": "keystone"
    ]
    res = ""
    for ( e in params ) {
        res = "${res}+${e.key}=${e.value}"
    }
    return res.substring(1)
}

/**
 * Run command with OpenStack env params and optional python env
 *
 * @param cmd    Command to be executed
 * @param env    Environmental parameters with endpoint credentials
 * @param path   Optional path to virtualenv with specific clients
 */
def runCommand(cmd, env, path = null) {
    withEnv([env]) {
        if (path) {
            output = python.runVirtualenvCommand(path, cmd)
        }
        else {
            echo("[Command]: ${cmd}")
            output = sh (
                script: cmd,
                returnStdout: true
            ).trim()
        }
    }
    return output
}

/**
 * Install OpenStack service clients in isolated environment
 *
 * @param path        Path where virtualenv is created
 * @param version     Version of the OpenStack clients
 */
def setupClientVirtualenv(path, version = 'kilo'){
    if(version == 'kilo') {
        requirements = openstack_kilo_packages
    }
    else if(version == 'liberty') {
        requirements = openstack_kilo_packages
    }
    else if(version == 'mitaka') {
        requirements = openstack_kilo_packages
    }
    else {
        requirements = openstack_latest_packages
    }
    python.setupVirtualenv(path, 'python2', requirements)
}

/**
 * Get OpenStack Keystone token for current credentials
 *
 * @param env          Connection parameters for OpenStack API endpoint
 * @param path         Optional path to the custom virtualenv
 */
def getKeystoneToken(env, path = null) {
    cmd = "keystone token-get"
    outputTable = runCommand(cmd, env, path)
    output = python.parseTextTable(outputTable, 'item', 'prettytable')
    return output
}

/**
 * Create new OpenStack Heat stack
 *
 * @param env          Connection parameters for OpenStack API endpoint
 * @param template     HOT template for the new Heat stack
 * @param environment  Environmentale parameters of the new Heat stack
 * @param name         Name of the new Heat stack
 * @param path         Optional path to the custom virtualenv
 */
def createHeatStack(env, template, environment, name, path = null) {
    stackEnvString = "parameters:\n"
    for ( int i = 0; i < environment.size; i++ ) {
        envString = "${envString}    ${environment.get(i).get(0)}: ${environment.get(i).get(1)}\n"
    }
    writeFile file: "heat.env", text: envString

    cmd = "heat stack-create -f ${template} -e heat.env ${name}"
    outputTable = runCommand(cmd, env, path)
    output = python.parseTextTable(outputTable, 'item', 'prettytable')

    i = 1
    while (true) {
        status = getHeatStackStatus(env, name, path)
        if (status == 'CREATE_FAILED') {
            info = getHeatStackResourceInfo(env, name, path)
            throw new Exception(info.stack_status_reason)
        }
        else if (status == 'CREATE_COMPLETE') {
            info = getHeatStackResourceInfo(env, name, path)
            echo(info.stack_status_reason)
            break
        }
        else
            echo("[Heat Stack] Status: ${status}, Check: ${i}")
        sh('sleep 5s')
        i++
    }
    echo("[Heat Stack] Status: ${status}")
}

/**
 * Get life cycle status for existing OpenStack Heat stack
 *
 * @param env          Connection parameters for OpenStack API endpoint
 * @param name         Name of the managed Heat stack instance
 * @param path         Optional path to the custom virtualenv
 */
def getHeatStackStatus(env, name, path = null) {
    cmd = 'heat stack-list | awk -v stack='+name+' \'{if ($4==stack) print $6}\''
    return runCommand(cmd, env, path)
}

/**
 * Get info about existing OpenStack Heat stack
 *
 * @param env          Connection parameters for OpenStack API endpoint
 * @param name         Name of the managed Heat stack instance
 * @param path         Optional path to the custom virtualenv
 */
def getHeatStackInfo(env, name, path = null) {
    cmd = "heat stack-show ${name}"
    outputTable = runCommand(cmd, env, path)
    output = python.parseTextTable(outputTable, 'item', 'prettytable')
}

/**
 * List all resources from existing OpenStack Heat stack
 *
 * @param env          Connection parameters for OpenStack API endpoint
 * @param name         Name of the managed Heat stack instance
 * @param path         Optional path to the custom virtualenv
 */
def getHeatStackResources(env, name, path = null) {
    cmd = "heat resource-list ${name}"
    outputTable = runCommand(cmd, env, path)
    output = python.parseTextTable(outputTable, 'list', 'prettytable')
}

/**
 * Get info about resource from existing OpenStack Heat stack
 *
 * @param env          Connection parameters for OpenStack API endpoint
 * @param name         Name of the managed Heat stack instance
 * @param path         Optional path to the custom virtualenv
 */
def getHeatStackResourceInfo(env, name, resource, path = null) {
    cmd = "heat resource-show ${name} ${resource}"
    outputTable = runCommand(cmd, env, path)
    output = python.parseTextTable(outputTable, 'item', 'prettytable')
}

/**
 * Update existing OpenStack Heat stack
 *
 * @param env          Connection parameters for OpenStack API endpoint
 * @param name         Name of the managed Heat stack instance
 * @param path         Optional path to the custom virtualenv
 */
def updateHeatStack(env, name, path = null) {
    cmd = "heat stack-update ${name}"
    outputTable = runCommand(cmd, env, path)
    output = python.parseTextTable(outputTable, 'item', 'prettytable')
}

/**
 * Delete existing OpenStack Heat stack
 *
 * @param env          Connection parameters for OpenStack API endpoint
 * @param name         Name of the managed Heat stack instance
 * @param path         Optional path to the custom virtualenv
 */
def deleteHeatStack(env, name, path = null) {
    cmd = "heat stack-delete ${name}"
    return = runCommand(cmd, env, path)
}

/**
 * Return list of servers from OpenStack Heat stack
 *
 * @param env          Connection parameters for OpenStack API endpoint
 * @param name         Name of the managed Heat stack instance
 * @param path         Optional path to the custom virtualenv
 */
def getHeatStackServers(env, name, path = null) {
    resources = heatGetStackResources(env, name, path)
    servers = []
    for (resource in resources) {
        if (resource.resource_type == 'OS::Nova::Server') {
            resourceName = resource.resource_name
            server = heatGetStackResourceInfo(env, name, resourceName, path)
            servers.add(server.attributes.name)
        }
    }
    echo("[Stack ${name}] Servers: ${servers}")
    return servers
}

return this;
