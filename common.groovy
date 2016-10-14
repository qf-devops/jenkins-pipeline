def getDatetime(format="yyyyMMddHHmmss") {
    def now = new Date();
    return now.format(format, TimeZone.getTimeZone('UTC'));
}

def getGitCommit() {
    git_commit = sh (
        script: 'git rev-parse HEAD',
        returnStdout: true
    ).trim()
    return git_commit
}

def abortBuild() {
    currentBuild.build().doStop()
    sleep(180)
    // just to be sure we will terminate
    throw new InterruptedException()
}

/**
 * Opposite of build-in parallel, run map of steps in serial
 *
 * @param steps Map of String<name>: CPSClosure2<step>
 */
def serial(steps) {
    for (singlestep in steps) {
        dummySteps = ["${singlestep.key}": singlestep.value]
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
 */
def agentSh(cmd) {
    sh(". ~/.ssh/ssh-agent.sh && ${cmd}")
}

/**
 * Ensure entry in SSH known hosts
 */
def ensureKnownHosts(url) {
    uri = new URI(url)
    port = uri.port ?: 22

    sh "test -f ~/.ssh/known_hosts && grep ${uri.host} ~/.ssh/known_hosts || ssh-keyscan -p ${port} ${uri.host} >> ~/.ssh/known_hosts"
}

/**
 * Mirror git repository
 */
def mirrorGit(sourceUrl, targetUrl, credentialsId, branches, followTags = false, gitEmail = 'jenkins@localhost', gitUsername = 'Jenkins') {
    prepareSshAgentKey(credentialsId)
    ensureKnownHosts(targetUrl)

    sh "git remote | grep target || git remote add target ${TARGET_URL}"
    agentSh "git remote update --prune"
    // TODO: doesn't work because of java.io.NotSerializableException: java.util.AbstractList$Itr
    //for (Iterator<String> branchIter = branches.iterator(); branchIter.hasNext();) {
    //branch = branchIter.next()
    branch = branches
    sh "git branch | grep ${branch} || git checkout -b ${branch} origin/${branch}"
    sh "git branch | grep ${branch} && git checkout ${branch} && git reset --hard origin/${branch}"

    sh "git config --global user.email '${gitEmail}'"
    sh "git config --global user.name '${gitUsername}'"
    sh "git ls-tree target/${branch} && git merge --no-edit --ff target/${branch} || echo 'Target repository is empty, skipping merge'"
    followTagsArg = followTags ? "--follow-tags" : ""
    agentSh "git push ${followTagsArg} target HEAD:${branch}"
    //}
}

return this;
