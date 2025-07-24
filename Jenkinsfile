@Library('cdn-devops') _

def RELEASE_BUILD
def TAG_NAME
String BUILD_RESULT = ""

pipeline {
    agent {
        label 'jnlp-slave'
    }
	options {
		buildDiscarder(logRotator(numToKeepStr: '10'))
		disableConcurrentBuilds()
		skipDefaultCheckout()
		timeout(time: 60, unit: 'MINUTES')
		gitLabConnection('gitlab')
	}
	parameters {
        choice(name: 'buildx', choices: ['linux/amd64', 'linux/arm64', 'linux/arm64,linux/amd64'], description: 'processor architecture')
    }
    environment {
        // 权限验证
        IMAGE_CREDENTIALS = "credential-harbor"
        // 镜像仓库地址
        IMAGE_REPOSITORY = "harbor.ctyuncdn.cn/datawings/${DEPLOYMENT_NAME}"
        // 镜像tag
        TAG_NAME = "${BRANCH_NAME}"
    }
    stages {
        // checkout code
        stage('Checkout') {
            steps {
                script {
                    container('tools') {
                        echo "当前tag为："
                        echo TAG_NAME
                        retry(2) { scmVars = checkout scm }
                        RELEASE_BUILD = scmVars.GIT_COMMIT
                        BUILD_RESULT = devops.updateBuildTasks(BUILD_RESULT,"Checkout OK...  √")
                        echo 'begin checkout...'
                        echo sh(returnStdout: true, script: "env")
                    }
                }
            }
        }

        // build code
        stage('Build-Package') {
            steps {
                script {
                    container('mvn-buildx') {
                        retry(2) {
                            // -Pstaging: include plugins
                            sh "mvn clean package -Pstaging -D'maven.test.skip=true' -D'checkstyle.skip=true'"
                        }
                        BUILD_RESULT = devops.updateBuildTasks(BUILD_RESULT,"Build-Package OK...√")
                    }
                }
            }
        }

        // unit test and sonar scan
        stage('CI'){
            failFast true
            parallel {
                stage('Unit Test') {
                    steps {
                        script {
                            container('tools') {
                                echo 'skip'
                            }
                        }
                    }
                }
                stage('Code Scan') {
                    steps {
                        script {
                            container('sonar') {
                                //devops.scan().start()
                                echo 'skip'
                            }
                        }
                    }
                }
            }
        }

        // build images
        stage('Build-Image') {
            steps {
                script {

                    def images = ["dolphinscheduler-alert", "dolphinscheduler-master", "dolphinscheduler-worker", "dolphinscheduler-api", "dolphinscheduler-tools"]
                    images.each { image ->
                        echo "image is ${image}"

                        container('ecx-docker-with-buildx') {
                            if ("${image}" == "dolphinscheduler-alert") {
                                builder = devops.dockerBuild(
                                        "${image}/dolphinscheduler-alert-server/src/main/docker/Dockerfile", //Dockerfile
                                        "dolphinscheduler-dist", // build context
                                        "harbor.ctyuncdn.cn/datawings/${image}-server", // repo address
                                        TAG_NAME, // tag
                                        IMAGE_CREDENTIALS, // credentials for pushing
                                )
                            } else {
                                builder = devops.dockerBuild(
                                        "${image}/src/main/docker/Dockerfile", //Dockerfile
                                        "dolphinscheduler-dist", // build context
                                        "harbor.ctyuncdn.cn/datawings/${image}", // repo address
                                        TAG_NAME, // tag
                                        IMAGE_CREDENTIALS, // credentials for pushing
                                )
                            }
                            builder.buildxAndPush(params.buildx)
                        }
                    }
                }
            }
        }


        // rename tar.gz
        stage('Rename tar.gz') {
            steps {
              container('tools') {
                script {

                    sh """
                        cd $WORKSPACE/dolphinscheduler-dist/target
                        ls -l apache-dolphinscheduler-*-bin.tar.gz
                        rm -rf apache-dolphinscheduler-*-bin.tar.gz
                        mv apache-dolphinscheduler-dev-SNAPSHOT-bin dolphinscheduler-3.3.0_ccdp_1.0.0
                        tar -zcf dolphinscheduler-3.3.0_ccdp_1.0.0.tar.gz dolphinscheduler-3.3.0_ccdp_1.0.0
                        ls -l dolphinscheduler-*.tar.gz
                    """
                  }
                }
            }
        }


        // upload tar to nexus repository
        stage('Deploy') {
            steps {
                script {
                    container('tools') {
                        withCredentials([usernamePassword(credentialsId: 'credential-nexus', passwordVariable: 'password', usernameVariable: 'user2')]) {
                             sh """
                                ls -l $WORKSPACE/dolphinscheduler-dist/target/dolphinscheduler-*.tar.gz
                                curl -v  -u sys_deployer:$password --upload-file $WORKSPACE/dolphinscheduler-dist/target/dolphinscheduler-3.3.0_ccdp_1.0.0.tar.gz https://devops.ctcdn.cn/nexus/repository/raw-repo/bigdata-emr-dev/emr-ccdp-dev-generic/emr-ccdp-tar-dev/
                             """

                             sh """
                                find . -type f -name "dolphinscheduler-*.tgz" | xargs rm -f
                                helm package $WORKSPACE/deploy/kubernetes/dolphinscheduler/
                                curl -v  -u sys_deployer:$password --upload-file dolphinscheduler-*.tgz https://devops.ctcdn.cn/nexus/repository/datawings-charts/
                             """
                        }
                    }
                }
            }
        }

    }

    post {
        success {
            script {
                container('tools') {
                    devops.notificationSuccess(DEPLOYMENT_NAME, "流水线完成了", RELEASE_BUILD, "dingTalk")
                }
            }
        }
        failure {
            script {
                container('tools') {
                    devops.notificationFailed(DEPLOYMENT_NAME, "流水线失败了", RELEASE_BUILD, "dingTalk")
                }
            }
        }
    }

}
