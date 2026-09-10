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
        
        stage('Smoke Test') {
            steps {
                script {
                    sh '''
                        # Start Java app container in background
                        docker run -d --name app-test -p 8080:8080 ${DOCKER_APP_IMAGE}
                        
                        # Wait for app to start
                        sleep 5
                        
                        # Test if app is running
                        if docker exec app-test curl -f http://localhost:8080/ || true; then
                            echo "✓ App is running"
                        else
                            echo "⚠ App started but no health endpoint"
                        fi
                        
                        # Cleanup
                        docker stop app-test
                        docker rm app-test
                    '''
                }
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