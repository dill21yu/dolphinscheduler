@Library('cdn-devops') _

def RELEASE_BUILD
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
    environment {
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


        // upload tar to nexus repository
        stage('Deploy') {
            steps {
                script {
                    container('tools') {
                        withCredentials([usernamePassword(credentialsId: 'credential-nexus', passwordVariable: 'password', usernameVariable: 'user2')]) {
                             sh """
                                ls -l $WORKSPACE/dolphinscheduler-dist/target/apache-dolphinscheduler-*.tar.gz
                                curl -v  -u sys_deployer:$password --upload-file $WORKSPACE/dolphinscheduler-dist/target/apache-dolphinscheduler-3.3.0_ccdp_1.0.0.tar.gz https://devops.ctcdn.cn/nexus/repository/raw-repo/bigdata-emr-dev/emr-ccdp-dev-generic/emr-ccdp-tar-dev/
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
