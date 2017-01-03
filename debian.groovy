/*
 * Build binary Debian package from existing dsc
 *
 * @param file  dsc file to build
 * @param image Image name to use for build (default debian:sid)
 */
def buildBinary(file, image="debian:sid") {
    def pkg = file.split('/')[-1].split('_')[0]
    def img = docker.image(image)
    img.withRun('-u root -e DEBIAN_FRONTEND=noninteractive') {c ->
        sh("apt-get update && apt-get install -y build-essential")
        sh("test -d build || mkdir build")
        sh("dpkg-source -x ${file} build/${pkg}")
        sh("cd build/${pkg}; apt-get update; dpkg-checkbuilddeps 2>&1|rev|cut -d : -f 1|rev|sed 's,(.*),,g'|xargs apt-get install -y")
        sh("cd build/${pkg}; debuild --no-lintian -uc -us")
    }
}

/*
 * Build source package from directory
 *
 * @param dir   Tree to build
 * @param image Image name to use for build (default debian:sid)
 */
def buildSource(dir, image="debian:sid") {
    if (new File("${dir}/.git").exists()) {
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
    img.withRun('-u root -e DEBIAN_FRONTEND=noninteractive') {c ->
        sh("apt-get update && apt-get install -y build-essential")
        sh("cd ${dir} && uscan --download-current-version")
        sh("cd ${dir} && dpkg-buildpackage -S -nc -uc -us")
    }
}

/*
 * Build source package using git-buildpackage
 *
 * @param dir   Tree to build
 * @param image Image name to use for build (default debian:sid)
 */
def buildSourceGbp(dir, image="debian:sid") {
    def img = docker.image(image)
    img.withRun('-u root -e DEBIAN_FRONTEND=noninteractive') {c ->
        sh("apt-get update && apt-get install -y build-essential git-buildpackage")
        sh("cd ${dir} && gbp buildpackage --git-notify=false --git-ignore-branch --git-ignore-new -S -uc -us")
    }
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
    img.withRun('-u root -e DEBIAN_FRONTEND=noninteractive') {c ->
        sh("apt-get update && apt-get install -y lintian")
        sh("lintian --no-tag-display-limit -Ii -E --pedantic --profile=${profile} '${changes}'")
    }
}

return this;
