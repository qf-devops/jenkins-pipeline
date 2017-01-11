/**
 *
 * Test reclass model validity
 *
 * Expected parameters:
 *   RECLASS_MODEL_URL          URL to git repo with Heat templates
 *   RECLASS_MODEL_CREDENTIALS  Credentials to the Heat templates repo
 *   RECLASS_MODEL_BRANCH       Heat templates repo branch
 *   SALT_MASTER_NAME           Salt master name (FQDN)
 *   SALT_MASTER_VERSION        Salt master version
 */

node {

    def modelEnv = "${env.WORKSPACE}/model"

    stage ('Download Reclass model') {
        checkoutGitRepository(modelEnv, RECLASS_MODEL_URL, RECLASS_MODEL_BRANCH, RECLASS_MODEL_CREDENTIALS)
    }

    stage('Test Reclass model') {
        testReclassModel(modelEnv, SALT_MASTER_NAME)
    }

}
