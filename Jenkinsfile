pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build Image') {
            steps {
                sh 'docker build -t team-skeleton:${BUILD_NUMBER} .'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn -B test'
                junit 'target/surefire-reports/*.xml'
            }
        }
    }
}