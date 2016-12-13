/** 
 *
 * Update Jenkins jobs with Salt master 
 *
 * Expected parameters:
 *   SALT_MASTER_URL            Salt API server address
 *   SALT_MASTER_CREDENTIALS    Credentials to the Salt API
 */

node {

    // connection objects
    def saltMaster

    stage("Connect to Salt master") {
        saltMaster = createSaltConnection(SALT_MASTER_URL, SALT_MASTER_CREDENTIALS)
    }

    stage("Update Jenkins jobs") {
        runSaltCommand(saltMaster, 'local', ["I@jenkins:client", "compound"], 'state.sls', ['jenkins.client'])
    }

}
