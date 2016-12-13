/** 
 *
 * Run Salt master process 
 *
 * Expected parameters:
 *   SALT_MASTER_URL            Salt API server address
 *   SALT_MASTER_CREDENTIALS    Credentials to the Salt API
 *   SALT_MASTER_PROCESS        Process to be run on Salt master
 */

node {

    // connection objects
    def saltMaster

    stage("Connect to Salt master") {
        saltMaster = createSaltConnection(SALT_MASTER_URL, SALT_MASTER_CREDENTIALS)
    }

    stage("Run Salt process") {
        runSaltProcess(saltMaster, SALT_MASTER_PROCESS)
    }

}
