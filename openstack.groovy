
// Load shared libs
common = evaluate(new File("${env.WORKSPACE}/libLoader/common.groovy"))
python = evaluate(new File("${env.WORKSPACE}/libLoader/python.groovy"))

openstack_kilo_packages = [
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

openstack_latest_packages = [
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

/**
 * create connection to OpenStack API endpoint
 *
 * @param url             OpenStack API endpoint address
 * @param credentialsId   Credentials to the OpenStack API
 * @param project         OpenStack project to connect to
 */
def createConnection(url, credentialsId, project) {
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
 * Run OpenStack command
 *
 * @param cmd    Command to be executed
 * @param env    Environmental parameters with endpoint credentials
 * @param path   Optional path to virtualenv with specific clients
 */
def runCommand(cmd, env, path = null) {
    if (path) {
        dir(path) {
            withEnv([env]) {
                virtualenv_cmd = ". ./bin/activate; ${cmd}"
                echo("[Command]: ${virtualenv_cmd}")
                output = sh (
                    script: virtualenv_cmd,
                    returnStdout: true
                ).trim()
            }
        }
    }
    else {
        withEnv([env]) {
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
 * Install OpenStack service clients
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
 * Get existing OpenStack Heat stack's life cycle status
 *
 * @param path         Path to template directory
 * @param rc           Connection parameters
 * @param template     Heat template name
 * @param environment  Heat environment
 * @param name         Heat stack name
 */
def getKeystoneToken(env, path = null) {
    cmd = "keystone token-get"
    output_table = runCommand(cmd, env, path)
    output = prettytableParseOutput(output_table, 'item')
    return output
}

/**
 * Create new OpenStack Heat stack
 *
 * @param path         Path to template directory
 * @param rc           Endpoint connection parameters
 * @param template     Heat template name
 * @param environment  Heat environment
 * @param name         Heat stack name
 */
def createHeatStack(path, rc, template, environment, name){
    stack_environment_string = "parameters:\n"
    for ( int i = 0; i < environment.size; i++ ) {
        environment_string = "${environment_string}    ${environment.get(i).get(0)}: ${environment.get(i).get(1)}\n"
    }
    writeFile file: "heat.env", text: environment_string
    sh ". ./${rc_file};. ./${path}/bin/activate; heat stack-create -f ${template} -e heat.env ${name}"
    i = 1
    while (true) {
        status = getHeaStackStatus(path, rc, name)
        if (status == 'CREATE_FAILED') {
            info = getHeatStackResourceInfo(path, rc, name)
            throw new Exception(info.stack_status_reason)
        }
        else if (status == 'CREATE_COMPLETE') {
            info = getHeatStackResourceInfo(path, rc, name)
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
 * Get existing OpenStack Heat stack's life cycle status
 *
 * @param path         Path to template directory
 * @param rc           Connection parameters
 * @param template     Heat template name
 * @param environment  Heat environment
 * @param name         Heat stack name
 */
def getHeatStackStatus(path, rc, name) {
    command = ". ./${rc_file};. ./${work_dir}/bin/activate; heat stack-list | awk -v stack="+stack_name+' \'{if ($4==stack) print $6}\''
    echo(command)
    output = sh (
        script: command,
        returnStdout: true
    ).trim()
    return output
}

/**
 * Get existing OpenStack Heat stack's info
 *
 * @param path         Path to the virtualenv directory
 * @param rc           
 * @param template     Heat template name
 * @param environment  Heat environment
 * @param name         Heat stack name
 */
def getHeatStackInfo(path, rc, name) {
    python.runVirtualenvCommand(path, 'python2')
    command = ". ./${rc_file};. ./${work_dir}/bin/activate; heat stack-show ${stack_name}"
    output_table = sh (
        script: command,
        returnStdout: true
    ).trim()
    return prettytableParseOutput(output_table, 'item')
}


def getHeatStackResources(work_dir, rc_file, stack_name) {
    command = ". ./${rc_file};. ./${work_dir}/bin/activate; heat resource-list ${stack_name}"
    output_table = sh (
        script: command,
        returnStdout: true
    ).trim()
    return prettytableParseOutput(output_table, 'list')
}

def getHeatStackResourceInfo(work_dir, rc_file, stack_name, resource_name) {
    command = ". ./${rc_file};. ./${work_dir}/bin/activate; heat resource-show ${stack_name} ${resource_name}"
    output_table = sh (
        script: command,
        returnStdout: true
    ).trim()
    return prettytableParseOutput(output_table, 'item')
}


def updateHeatStack(work_dir, rc_file, stack_name) {
    command = ". ./${rc_file};. ./${work_dir}/bin/activate; heat stack-update ${stack_name}"
    output = sh (
        script: command,
        returnStdout: true
    ).trim()
    return output
}


def deleteHeatStack(work_dir, rc_file, stack_name) {
    command = ". ./${rc_file};. ./${work_dir}/bin/activate; heat stack-delete ${stack_name}"
    output = sh (
        script: command,
        returnStdout: true
    ).trim()
    return output
}

def getHeatStackServers(work_dir, rc_file, stack_name) {
    resources = heatGetStackResources(work_dir, rc_file, stack_name) 
    servers = []
    for (resource in resources) {
        if (resource.resource_type == 'OS::Nova::Server') {
            resource_name = resource.resource_name
            server = heatGetStackResourceInfo(work_dir, rc_file, stack_name, resource_name)
            servers.add(server.attributes.name)
        }
    }
    echo("[Stack servers] ${servers}")
    return servers
}

return this;
