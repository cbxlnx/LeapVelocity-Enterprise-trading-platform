pipeline {
    agent any
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Verify Java') {
            steps {
                sh 'java -version && mvn -version'
            }
        }
        stage('Build') {
            steps {
                sh 'mvn -B clean package'
            }
        }
        stage('Build Image') {
            steps {
                sh 'docker build -t team-skeleton:latest .'
            }
        }
        stage('Smoke Test') {
            steps {
                sh 'docker run --rm team-skeleton:latest'
            }
        }
    }
    post {
        always {
            script {
                githubNotify(
                    status: currentBuild.result == 'SUCCESS' ? 'SUCCESS' : 'FAILURE',
                    context: 'continuous-integration/jenkins/multibranch-pipeline-new',
                    description: 'Jenkins build result'
                )
            }
        }
    }
}
