/**
 *
 * Launch and test heat stack based deployments
 *
 * Expected parameters:
 *   PIPELINE_LIB_URL           URL to git repo with shared pipeline libs
 *   PIPELINE_LIB_CREDENTIALS   Credentials to the shared pipeline libs repo
 *   PIPELINE_LIB_BRANCH        Branch to checkout from pipeline libs repo
 *   SALT_MASTER_URL            Salt API server address
 *   SALT_MASTER_CREDENTIALS    Credentials to the Salt API
 *   SALT_MINION_TARGETS        Compound match expression to target minions
 *   HEAT_TEMPLATE_URL          URL to git repo with Heat templates
 *   HEAT_TEMPLATE_CREDENTIALS  Credentials to the Heat templates repo
 *   HEAT_TEMPLATE_BRANCH       Heat templates repo branch
 *   OPENSTACK_API_URL          OpenStack API address
 *   OPENSTACK_API_CREDENTIALS  Credentials to the OpenStack API
 *   OPENSTACK_API_PROJECT      OpenStack project to connect to
 *   OPENSTACK_API_CLIENT       Versions of OpenStack python clients
 *   OPENSTACK_API_VERSION      Version of the OpenStack API (2/3)
 *   HEAT_STACK_NAME            Heat stack name
 *   HEAT_STACK_TEMPLATE        Heat stack HOT template
 *   HEAT_STACK_ENVIRONMENT     Heat stack environmental parameters
 *   HEAT_STACK_ZONE            Heat stack availability zone
 *   HEAT_STACK_PUBLIC_NET      Heat stack floating IP pool
 */

// core libs
def git
def openstack
def salt

// connection objects
def salt_master
def openstack_cloud

// value defaults
def targetExpression = SALT_MINION_TARGETS ? SALT_MINION_TARGETS : "I@salt:master"
def openstackVersion = OPENSTACK_API_CLIENT ? OPENSTACK_API_CLIENT : "liberty"

node {

    stage("Install core pipeline lib") {
        fileLoader.withGit(PIPELINE_LIB_URL, PIPELINE_LIB_BRANCH, PIPELINE_LIB_CREDENTIALS, '') {
            git = fileLoader.load("git");
            openstack = fileLoader.load("openstack");
            salt = fileLoader.load("salt");
        }
    }

    stage("Connect to Salt master") {
        salt_master = salt.createConnection(SALT_MASTER_URL, SALT_MASTER_CREDENTIALS)
    }

    stage ('Download Heat templates') {
        git.checkoutRepository('template', HEAT_TEMPLATE_URL, HEAT_TEMPLATE_BRANCH, HEAT_TEMPLATE_CREDENTIALS)
    }

    stage('Install OpenStack env') {
        openstack.setupClientVirtualenv('venv', openstackVersion)
    }

    stage('Connect to OpenStack cloud') {
        openstack_cloud = openstack.createEnvParams(OPENSTACK_API_URL, OPENSTACK_API_CREDENTIALS, OPENSTACK_API_PROJECT)
        openstack.getKeystoneToken(openstack_cloud, 'venv')
    }

    stage('Launch new Heat stack') {
        openstack.createHeatStack(openstack_cloud, HEAT_STACK_TEMPLATE, [], HEAT_STACK_NAME, 'venv')
    }

    stage('Delete created Heat stack') {
        openstack.deleteHeatStack(openstack_cloud, HEAT_STACK_NAME, 'venv')
    }

}
