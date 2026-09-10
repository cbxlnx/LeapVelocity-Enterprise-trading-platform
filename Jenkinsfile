pipeline {
    agent any
    
    environment {
        DOCKER_APP_IMAGE = "team-skeleton:latest"
    }
    
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
        
        stage('Build Java App Image') {
            steps {
                sh 'docker build -t ${DOCKER_APP_IMAGE} .'
            }
        }
        
        stage('Verify Build') {
            steps {
                sh '''
                    # Verify JAR exists and is valid
                    test -f /build/target/team-skeleton.jar && echo "✓ JAR built successfully"
                    jar tf /build/target/team-skeleton.jar | head -5
                '''
            }
        }
        
        stage('Push Image') {
            when {
                branch 'main'
            }
            steps {
                sh 'echo "Image ${DOCKER_APP_IMAGE} ready for deployment"'
            }
        }
    }
    
    post {
        always {
            sh 'docker image prune -f || true'
        }
        failure {
            echo 'Pipeline failed!'
        }
        success {
            echo '✓ Pipeline completed successfully!'
        }
    }
}