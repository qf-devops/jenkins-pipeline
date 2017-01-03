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

def cleanup(image="debian:sid") {
    def img = docker.image(image)

    workspace = getWorkspace()
    sh("docker run -e DEBIAN_FRONTEND=noninteractive -v ${workspace}:${workspace} -w ${workspace} --rm=true --privileged ${image} /bin/bash -c 'rm -rf build-area || true'")
}

/*
 * Build binary Debian package from existing dsc
 *
 * @param file  dsc file to build
 * @param image Image name to use for build (default debian:sid)
 */
def buildBinary(file, image="debian:sid") {
    def pkg = file.split('/')[-1].split('_')[0]
    def img = docker.image(image)

    workspace = getWorkspace()
    sh("docker run -e DEBIAN_FRONTEND=noninteractive -v ${workspace}:${workspace} -w ${workspace} --rm=true --privileged ${image} /bin/bash -c 'apt-get update && apt-get install -y build-essential devscripts && dpkg-source -x ${file} build-area/${pkg} && cd build-area/${pkg} && dpkg-checkbuilddeps 2>&1|rev|cut -d : -f 1|rev|sed \"s,(.*),,g\"|xargs apt-get install -y && debuild --no-lintian -uc -us -b'")
}

/*
 * Build source package from directory
 *
 * @param dir   Tree to build
 * @param image Image name to use for build (default debian:sid)
 */
def buildSource(dir, image="debian:sid") {
    def isGit
    try {
        sh("test -d ${dir}/.git")
        isGit = true
    } catch (Exception e) {
        isGit = false
    }

    if (isGit == true) {
        buildSourceGbp(dir, image)
    } else {
        buildSourceUscan(dir, image)
    }
}

/*
 * Build source package, fetching upstream code using uscan
 *
 * @param dir   Tree to build
 * @param image Image name to use for build (default debian:sid)
 */
def buildSourceUscan(dir, image="debian:sid") {
    def img = docker.image(image)
    workspace = getWorkspace()
    sh("docker run -e DEBIAN_FRONTEND=noninteractive -v ${workspace}:${workspace} -w ${workspace} --rm=true --privileged ${image} /bin/bash -c 'apt-get update && apt-get install -y build-essential devscripts && cd ${dir} && uscan --download-current-version && dpkg-buildpackage -S -nc -uc -us'")
}

/*
 * Build source package using git-buildpackage
 *
 * @param dir   Tree to build
 * @param image Image name to use for build (default debian:sid)
 */
def buildSourceGbp(dir, image="debian:sid") {
    def img = docker.image(image)
    workspace = getWorkspace()
    sh("docker run -e DEBIAN_FRONTEND=noninteractive -v ${workspace}:${workspace} -w ${workspace} --rm=true --privileged ${image} /bin/bash -c 'apt-get update && apt-get install -y build-essential git-buildpackage && cd ${dir} && gbp buildpackage -nc --git-force-create --git-notify=false --git-ignore-branch --git-ignore-new --git-verbose -S -uc -us'")
}

/*
 * Run lintian checks
 *
 * @param changes   Changes file to test against
 * @param profile   Lintian profile to use (default debian)
 * @param image     Image name to use for build (default debian:sid)
 */
def runLintian(changes, profile="debian", image="debian:sid") {
    def img = docker.image(image)
    workspace = getWorkspace()
    sh("docker run -e DEBIAN_FRONTEND=noninteractive -v ${workspace}:${workspace} -w ${workspace} --rm=true --privileged ${image} /bin/bash -c 'apt-get update && apt-get install -y lintian && cd ${dir} && lintian --no-tag-display-limit -Ii -E --pedantic --profile=${profile} ${changes}'")
}

return this;
