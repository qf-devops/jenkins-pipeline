
/**
 * Install python virtualenv
 *
 * @param path     Path to virtualenv
 * @param python   Version of Python (python/python3)
 * @param reqs     Environment requirements in list format    
 */
def setupVirtualenv(path, python = 'python2', reqs = []) {
    virtualenv_cmd = "virtualenv ${path} --python ${python}"

    echo("[Python ${path}] Setup ${python} environment")
    sh(returnStdout: true, script: virtualenv_cmd)
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
    virtualenv_cmd = ". ./bin/activate; ${cmd}"
    dir(path) {
        echo("[Python ${path}] Run command ${cmd}")
        output = sh(
            returnStdout: true,
            script: virtualenv_cmd
        ).trim()
    }
    return output
}

/**
 * Parse content from markup-text tables to variables
 *
 * @param tableStr   String representing the table
 * @param mode       Either list (1st row are keys) or item (key, value rows)
 * @param format     Format of the table
 */
def parseTextTable(tableStr, mode = 'item', format = 'rest') {
    tableFile = 'prettytable.txt'
    writeFile file: tableFile, text: tableStr
    parserFile = "scripts/parse_${mode}_table.py"
    raw_data = sh (
        script: "python ./${parserFile}",
        returnStdout: true
    ).trim()
    data = new groovy.json.JsonSlurperClassic().parseText(raw_data)
    echo("[Parsed ${format} table] ${data}")
    return data
}

return this;
