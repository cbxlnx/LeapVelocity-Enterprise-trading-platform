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
                sh 'mvn -B clean package -pl backend,execution-engine'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn -B test -pl backend,execution-engine'
            }
        }
        
        stage('Build Java App Image') {
            steps {
                sh 'docker build -t ${DOCKER_APP_IMAGE} -f backend/Dockerfile backend/'
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

        stage('Quality Gate') {
            steps {
                withSonarQubeEnv('sonar-server') {
                    sh '''
                        mvn -B sonar:sonar \
                        -Dsonar.projectKey=leapvelocity-platform \
                        -Dsonar.qualitygate.wait=true
                    '''
                }
            }
        }

        stage('Security Scans') {
            parallel {
                stage('Dependency Vulnerabilities') {
                    steps {
                        sh '''
                            docker run --rm \
                            -v $(pwd):/workspace \
                            -w /workspace \
                            aquasec/trivy:0.57.1 fs \
                            --exit-code 1 \
                            --severity HIGH,CRITICAL \
                            --scanners vuln \
                            .
                        '''
                    }
                }

                stage('Secret Detection') {
                    steps {
                        sh '''
                            docker run --rm -v $(pwd):/repo -w /repo \
                            zricethezav/gitleaks:latest detect \
                            --verbose
                        '''
                    }
                }
            }
        }
        
        stage('Push Image') {
            when {
                branch 'main'
                branch 'develop'
                branch pattern: 'feat/.*', comparator: 'REGEXP'
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
