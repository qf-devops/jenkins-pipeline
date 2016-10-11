/**
 * Build step to build docker image. For use with eg. parallel
 *
 * @param img           Image name
 * @param baseImg       Base image to use (can be empty)
 * @param dockerfile    Dockerfile to use
 * @param timestamp     Image tag
 */
def buildDockerImageStep(img, baseImg, dockerfile, timestamp) {
    File df = new File(dockerfile);
    return {
        if (baseImg) {
            sh "git checkout -f ${dockerfile}; sed -i -e 's,^FROM.*,FROM ${baseImg},g' ${dockerfile}"
        }
        docker.build(
            "${img}:${timestamp}",
            [
                "${dockerfile}",
                df.getParent()
            ].join(' ')
        )
    }
}

return this;
