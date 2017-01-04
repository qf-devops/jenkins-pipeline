/**
 *
 * Deploy MkXX lab with salt
 *
 * Expected parameters:
 *   SALT_MASTER_URL            Salt API address
 *   SALT_MASTER_CREDENTIALS    Credentials to the Salt API
 */

node {

    // connection objects
    def saltMaster

    stage("Connect to Salt master") {
        saltMaster = createSaltConnection(SALT_MASTER_URL, SALT_MASTER_CREDENTIALS)
    }

    stage("Install core infra") {
        installFoundationInfra(saltMaster)
        validateFoundationInfra(saltMaster)
    }

    stage("Install OpenStack infra") {
        installOpenstackMkInfra(saltMaster)
    }

    stage("Install OpenStack control") {
        installOpenstackMkControl(saltMaster)
    }

    stage("Install OpenContrail control") {
        installOpenstackMkNetwork(saltMaster)
    }

    stage("Install OpenStack compute") {
        installOpenstackMkCompute(saltMaster)
    }

    stage("Install StackLight control") {
        installStacklightControl(saltMaster)
    }

    stage("Install StackLight clients") {
        installStacklightClient(saltMaster)
    }

    stage("Run tests on OpenStack cloud") {
    //    validateOpenstackMkControl(saltMaster)
    }

    //stage('Delete Heat stack') {
    //    deleteHeatStack(openstackCloud, HEAT_STACK_NAME, openstackEnv)
    //}

}
