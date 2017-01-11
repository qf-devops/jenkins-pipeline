/**
 *
 * Generate reclass OpenStack MK cluster model with OpenVSwitch with cookiecutter
 *
 * Expected parameters:
 *   COOKIECUTTER_TEMPLATE_URL          URL to git repo with Cookiecutter templates
 *   COOKIECUTTER_TEMPLATE_CREDENTIALS  Credentials to the Cookiecutter templates repo
 *   COOKIECUTTER_TEMPLATE_BRANCH       Cookiecutter templates repo branch
 *   COOKIECUTTER_TEMPLATE_PATH         Path to specific Cookiecutter template
 *   RECLASS_CLUSTER_NAME               New reclass cluster name
 *   RECLASS_CLUSTER_PARAMETERS         New reclass cluster context in JSON format
 *   RECLASS_MODEL_URL                  URL to git repo to store the generated cluster
 *   RECLASS_MODEL_CREDENTIALS          Credentials to the reclass metadata repository
 *   RECLASS_MODEL_BRANCH               Reclass repository branch
 */

node {

    def templateEnv = "${env.WORKSPACE}/template"
    def modelEnv = "${env.WORKSPACE}/model"

    stage ('Download Cookiecutter template') {
        checkoutGitRepository(templateEnv, COOKIECUTTER_TEMPLATE_URL, COOKIECUTTER_TEMPLATE_BRANCH, COOKIECUTTER_TEMPLATE_CREDENTIALS)
    }

    stage ('Download full Reclass model') {
        checkoutGitRepository(modelEnv, RECLASS_MODEL_URL, RECLASS_MODEL_BRANCH, RECLASS_MODEL_CREDENTIALS)
    }

    stage('Generate Reclass cluster fragment') {
        generateReclassModel(templateEnv, SALT_MASTER_NAME)
    }

    stage('Inject Reclass cluster fragment to full model') {
        generateReclassModel(modelEnv, SALT_MASTER_NAME)
    }

    stage ('Upload full Reclass model as new branch') {
        checkoutGitRepository(modelEnv, RECLASS_MODEL_URL, RECLASS_MODEL_BRANCH, RECLASS_MODEL_CREDENTIALS)
    }

}
