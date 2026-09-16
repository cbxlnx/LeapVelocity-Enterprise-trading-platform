pipeline {
    agent any
    
    environment {
        DOCKER_APP_IMAGE = "leapvelocity-backend:latest"
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
                sh 'mvn -B -f backend/pom.xml clean package'
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
                    test -n "$(find backend/target -maxdepth 1 -name '*.jar' -print -quit)" && echo "✓ JAR built successfully"
                    jar tf "$(find backend/target -maxdepth 1 -name '*.jar' -print -quit)" | head -5
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
