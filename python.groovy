
/**
 * Install python virtualenv
 *
 * @param path     Path to virtualenv
 * @param python   Version of Python (python/python3)
 * @param reqs     Environment requirements in list format    
 */
def setupVirtualenv(path, python, reqs) {
    echo("virtualenv ${path} --python ${python}")
    sh(returnStdout: true, script: "virtualenv ${path} --python ${python}")
    args = ""
    for (req in reqs) {
        args = args + "${req}\n"
    }
    writeFile file: "${path}/requirements.txt", text: args
    runVirtualenvCommand(path, "pip install -r ./requirements.txt")
}

/**
 * Run command in specific python virtualenv
 *
 * @param path   Path to virtualenv
 * @param cmd    Command to be executed
 */
def runVirtualenvCommand(path, cmd) {
    dir(path) {
        echo(". ./bin/activate; ${cmd}")
        sh(returnStdout: true, script: ". ./bin/activate; ${cmd}")
    }
}

return this;
