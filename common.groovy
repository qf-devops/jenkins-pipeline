/**
 * Generate current timestamp
 *
 * @param format    Defaults to yyyyMMddHHmmss
 */
def getDatetime(format="yyyyMMddHHmmss") {
    def now = new Date();
    return now.format(format, TimeZone.getTimeZone('UTC'));
}

/**
 * Parse HEAD of current directory and return commit hash
 */
def getGitCommit() {
    git_commit = sh (
        script: 'git rev-parse HEAD',
        returnStdout: true
    ).trim()
    return git_commit
}

/**
 * Return workspace.
 * Currently implemented by calling pwd so it won't return relevant result in
 * dir context
 */
def getWorkspace() {
    def workspace = sh script: 'pwd', returnStdout: true
    workspace = workspace.trim()
    return workspace
}

/**
 * Abort build, wait for some time and ensure we will terminate
 */
def abortBuild() {
    currentBuild.build().doStop()
    sleep(180)
    // just to be sure we will terminate
    throw new InterruptedException()
}

/**
 * Print informational message
 *
 * @param msg
 * @param color Colorful output or not
 */
def info(msg, color = true) {
    printMsg(msg, "INFO", "cyan")
}

/**
 * Print error message
 *
 * @param msg
 * @param color Colorful output or not
 */
def error(msg, color = true) {
    printMsg(msg, "INFO", "red")
}

/**
 * Print success message
 *
 * @param msg
 * @param color Colorful output or not
 */
def success(msg, color = true) {
    printMsg(msg, "INFO", "green")
}

/**
 * Print warning message
 *
 * @param msg
 * @param color Colorful output or not
 */
def warning(msg, color = true) {
    printMsg(msg, "INFO", "yellow")
}

/**
 * Print message
 *
 * @param msg
 * @param level Level of message (default INFO)
 * @param color Color to use for output or false (default)
 */
def printMsg(msg, level = "INFO", color = false) {
    colors = [
        'red'   : '\u001B[31m',
        'black' : '\u001B[30m',
        'green' : '\u001B[32m',
        'yellow': '\u001B[33m',
        'blue'  : '\u001B[34m',
        'purple': '\u001B[35m',
        'cyan'  : '\u001B[36m',
        'white' : '\u001B[37m',
        'reset' : '\u001B[0m'
    ]
    if (color != false) {
        wrap([$class: 'AnsiColorBuildWrapper']) {
            print "${colors[color]}[${level}] ${msg}${colors.reset}"
        }
    } else {
        print "[${level}] ${msg}"
    }
}

/**
 * Traverse directory structure and return list of files
 *
 * @param path Path to search
 * @param type Type of files to search (groovy.io.FileType.FILES)
 */
@NonCPS
def getFiles(path, type=groovy.io.FileType.FILES) {
    files = []
    new File(path).eachFile(type) {
        files[] = it
    }
    return files
}

/**
 * Helper method to convert map into form of list of [key,value] to avoid
 * unserializable exceptions
 *
 * @param m Map
 */
@NonCPS
def entries(m) {
    m.collect {k, v -> [k, v]}
}

/**
 * Opposite of build-in parallel, run map of steps in serial
 *
 * @param steps Map of String<name>: CPSClosure2<step>
 */
def serial(steps) {
    stepsArray = entries(steps)
    for (i=0; i < stepsArray.size; i++) {
        s = stepsArray[i]
        dummySteps = ["${s[0]}": s[1]]
        parallel dummySteps
    }
}

/**
 * Checkout git repositories in parallel
 *
 * @param dir   Directory to checkout to
 * @param url   Git repository url
 * @param branch        Git repository branch
 * @param credentialsId Credentials id to use
 * @param poll          Poll automatically
 * @param clean         Clean status
 */
def gitCheckoutStep(directory, url, branch, credentialsId  = null, poll = true, clean = true) {
    return {
        print "Checking out ${url}, branch ${branch} into ${directory}"
        dir(directory) {
            git url: url, branch: branch, credentialsId: credentialsId, poll: poll, clean: clean
        }
    }
}

/**
 * Get SSH credentials from store
 *
 * @param id    Credentials name
 */
def getSshCredentials(id) {
    def creds = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
                    com.cloudbees.plugins.credentials.common.StandardUsernameCredentials.class,
                    jenkins.model.Jenkins.instance
                )

    for (Iterator<String> credsIter = creds.iterator(); credsIter.hasNext();) {
        c = credsIter.next();
        if ( c.id == id ) {
            return c;
        }
    }

    throw new Exception("Could not find credentials for ID ${id}")
}

/**
 * Setup ssh agent and add private key
 *
 * @param credentialsId Jenkins credentials name to lookup private key
 */
def prepareSshAgentKey(credentialsId) {
    c = getSshCredentials(credentialsId)
    sh("test -d ~/.ssh || mkdir -m 700 ~/.ssh")
    sh('pgrep -l -u $USER -f | grep -e ssh-agent\$ >/dev/null || ssh-agent|grep -v "Agent pid" > ~/.ssh/ssh-agent.sh')
    sh("echo '${c.getPrivateKey()}' > ~/.ssh/id_rsa_${credentialsId} && chmod 600 ~/.ssh/id_rsa_${credentialsId}")
    agentSh("ssh-add ~/.ssh/id_rsa_${credentialsId}")
}

/**
 * Execute command with ssh-agent
 *
 * @param cmd   Command to execute
 */
def agentSh(cmd) {
    sh(". ~/.ssh/ssh-agent.sh && ${cmd}")
}

/**
 * Ensure entry in SSH known hosts
 *
 * @param url   url of remote host
 */
def ensureKnownHosts(url) {
    def hostArray = getKnownHost(url)
    sh "test -f ~/.ssh/known_hosts && grep ${hostArray[0]} ~/.ssh/known_hosts || ssh-keyscan -p ${hostArray[1]} ${hostArray[0]} >> ~/.ssh/known_hosts"
}

@NonCPS
def getKnownHost(url){
     // test for git@github.com:organization/repository like URLs
    def p = ~/.+@(.+\..+)\:{1}.*/
    def result = p.matcher(url)
    def host = ""
    if (result.matches()) {
        host = result.group(1)
        port = 22
    } else {
        parsed = new URI(url)
        host = parsed.host
        port = parsed.port && parsed.port > 0 ? parsed.port: 22
    }
    return [host,port]
}

/**
 * Mirror git repository
 */
def mirrorGit(sourceUrl, targetUrl, credentialsId, branches, followTags = false, gitEmail = 'jenkins@localhost', gitUsername = 'Jenkins') {
    if (branches instanceof String) {
        branches = branches.tokenize(',')
    }

    prepareSshAgentKey(credentialsId)
    ensureKnownHosts(targetUrl)

    sh "git remote | grep target || git remote add target ${TARGET_URL}"
    agentSh "git remote update --prune"
    for (i=0; i < branches.size; i++) {
        branch = branches[i]
        sh "git branch | grep ${branch} || git checkout -b ${branch} origin/${branch}"
        sh "git branch | grep ${branch} && git checkout ${branch} && git reset --hard origin/${branch}"

        sh "git config --global user.email '${gitEmail}'"
        sh "git config --global user.name '${gitUsername}'"
        sh "git ls-tree target/${branch} && git merge --no-edit --ff target/${branch} || echo 'Target repository is empty, skipping merge'"
        followTagsArg = followTags ? "--follow-tags" : ""
        agentSh "git push ${followTagsArg} target HEAD:${branch}"
    }
    if (followTags == true) {
        agentSh "git push target --tags"
    }
}

/**
 * Tests Jenkins instance for existence of plugin with given name
 * @param pluginName plugin short name to test
 * @return boolean result
 */
@NonCPS
def jenkinsHasPlugin(pluginName){
    return Jenkins.instance.pluginManager.plugins.collect{p -> p.shortName}.contains(pluginName)
}

/**
 * Send notification to all enabled notifications services
 * @param buildStatus message type (success, warning, error), null means SUCCESSFUL
 * @param msgText message text
 * @param enabledNotifications list of enabled notification types, types: slack, hipchat, email, default empty
 * @param notificatedTypes types of notifications will be sent, default all - ["successful","unstable","failed"]
 * @param jobName optional job name param, if empty env.JOB_NAME will be used
 * @param buildNumber build number param, if empty env.JOB_NAME will be used
 * @param buildUrl build url param, if empty env.JOB_NAME will be used
 * @param mailFrom mail FROM param, if empty "jenkins" will be used, it's mandatory for sending email notifications
 * @param mailTo mail TO param, it's mandatory for sending email notifications
 */ 
def sendNotification(buildStatus, msgText="",enabledNotifications = [] notificatedTypes=["successful","unstable","failed"], jobName=null, buildNumber=null, buildUrl=null, mailFrom="jenkins", mailTo=null){
    // Default values
    def colorName = 'blue'
    def colorCode = '#0000FF'
    def buildStatusParam = buildStatus != null && buildStatus != "" ? buildStatus : "SUCCESSFUL"
    def jobNameParam = jobName != null && jobName != "" ? jobName : env.JOB_NAME
    def buildNumberParam = buildNumber != null && buildNumber != "" ? buildNumber : env.BUILD_NUMBER 
    def buildUrlParam = buildUrl != null && buildUrl != "" ? buildUrl : env.BUILD_URL 
    def subject = "${buildStatusParam}: Job '${jobNameParam} [${buildNumberParam}]'"
    def summary = "${subject} (${buildUrlParam})"
    if(msgText != null && msgText != ""){
        summary+="\n${msgText}"
    }
    if(buildStatusParam.toLowerCase().equals("successful")){
        colorCode = "#00FF00"
        colorName = "green"
    }else if(buildStatusParam.toLowerCase().equals("unstable")){
        colorCode = "#FFFF00"
        colorName = "yellow"
    }else if(buildStatusParam.toLowerCase().equals("failed")){
        colorCode = "#FF0000"
        colorName = "red"
    }
    if(notificatedTypes.contains(buildStatusParam.toLowerCase())){
        if(enabledNotifications.contains("slack") && jenkinsHasPlugin("slack")){
            try{
                slackSend color: colorCode, message: summary
            }catch(Exception e){
                println("Calling slack plugin failed")
                e.printStackTrace()
            }
        }
        if(enabledNotifications.contains("hipchat") && jenkinsHasPlugin("hipchat")){
            try{
                hipchatSend color: colorName.toUpperCase(), message: summary
            }catch(Exception e){
                println("Calling hipchat plugin failed")
                e.printStackTrace()
            }
        }
        if(enabledNotifications.contains("email") && mailTo != null && mailTo != "" && mailFrom != null && mailFrom != ""){
            try{
                mail body: summary, from: mailFrom, subject: subject, to: mailTo
            }catch(Exception e){
                println("Sending mail plugin failed")
                e.printStackTrace()
            }
        }
    }
}

return this;
