pipeline {
    agent any

    environment {
        DOCKER_HUB_CREDENTIALS = credentials('dockerhub-credentials')
        DOCKER_IMAGE           = "your-dockerhub-username/statistic-chart-generator"
        DOCKER_TAG             = "${env.BUILD_NUMBER}"
    }

    tools {
        maven 'Maven 3'
        jdk   'JDK 17'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh 'mvn -B clean package -DskipTests --file pom.xml'
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh """
                    docker build \
                        -t ${DOCKER_IMAGE}:${DOCKER_TAG} \
                        -t ${DOCKER_IMAGE}:latest \
                        .
                """
            }
        }

        stage('Docker Push') {
            steps {
                sh """
                    echo "${DOCKER_HUB_CREDENTIALS_PSW}" | \
                        docker login -u "${DOCKER_HUB_CREDENTIALS_USR}" --password-stdin
                    docker push ${DOCKER_IMAGE}:${DOCKER_TAG}
                    docker push ${DOCKER_IMAGE}:latest
                """
            }
        }

        stage('Deploy') {
            when {
                expression { return false }
            }
            steps {
                sh """
                    sed 's/IMAGE_TAG/${DOCKER_TAG}/g' k8s.yaml | kubectl apply -f -
                """
            }
        }

        stage('Git Tag') {
            steps {
                sh """
                    git tag build-${DOCKER_TAG}
                    git push origin build-${DOCKER_TAG}
                """
            }
        }
    }

    post {
        always {
            sh 'docker logout'
            sh "docker rmi ${DOCKER_IMAGE}:${DOCKER_TAG} ${DOCKER_IMAGE}:latest || true"
        }
        failure {
            echo 'Pipeline failed.'
        }
        success {
            echo "Image pushed: ${DOCKER_IMAGE}:${DOCKER_TAG}"
        }
    }
}
