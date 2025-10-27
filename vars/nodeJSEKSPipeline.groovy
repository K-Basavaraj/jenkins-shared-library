//this is function define a function name call()
def call(Map configMap) {
    pipeline {
        agent {
          label 'AGENT-1'
        }
        options {
          timeout(time: 5, unit: 'MINUTES')
          disableConcurrentBuilds()
        }
        parameters {
          choice(name: 'ENVIRONMENT', choices: ['dev', 'qa', 'uat', 'pre-prod', 'prod'], description: 'Select your environment') 
          booleanParam(name: 'deploy', defaultValue: false, description: 'select to deploy or not')
        }
        environment {
          region = 'us-east-1'
          account_id = '688567303455'
          project = configMap.get("project")
          component = configMap.get("component")
        }

        stages{
            stage('Read The Version') {
              steps {
                script {
                  // Read the version from package.json
                  // Store it in a Jenkins environment variable (env.appVersion) so it is available globally
                  // across all stages (Docker build, Deploy, etc.), not just inside this script block
                  def packageJson = readJSON file: 'Backend/package.json'
                  env.appVersion = packageJson.version
                  echo "App version: ${env.appVersion}"
                }
              }
            }

            stage('Initialize Environment') {
              steps {
                script {
                 // This allows us to use the selected environment in all stages (Docker build, deploy, etc.)
                  echo "Selected environment: ${params.ENVIRONMENT}"
                }
              }
            }

            stage('install dependencies'){
              steps {
                dir('Backend') {
                  sh 'npm install'
                }
              }
            }

            stage('Docker Build'){
              steps {
                withAWS(region: 'us-east-1', credentials: 'aws-creds'){
                  dir('Backend'){
                    script {
                      def repo = "${account_id}.dkr.ecr.${region}.amazonaws.com/${project}/${params.ENVIRONMENT}/${component}:${env.appVersion}"
                      sh """
                          echo "Logging into ECR..."
                          aws ecr get-login-password --region ${region} | docker login --username AWS --password-stdin ${account_id}.dkr.ecr.${region}.amazonaws.com

                          echo "Building Docker image: ${repo}"
                          docker build -t ${repo} .

                          docker images

                          echo "Pushing image to ECR..."
                          docker push ${repo}

                          echo "Docker image successfully pushed to ${repo}"
                      """
                    }
                  }
                }
              }
            }

            stage('Deploy'){
              when {
                expression { params.deploy }
              }
              steps {
                echo "Triggering deployment job for environment: ${params.ENVIRONMENT}"
                build job: 'Backend-CD', parameters: [string(name: 'version', value: "${env.appVersion}"), string(name: 'ENVIRONMENT', value: "${params.ENVIRONMENT}")], wait: true      
              }
            }
        }
        post {
          always {
            echo 'This section always runs!'
            deleteDir() //in jenkins this function will delete the workspace directory of the job on the agent (VM/container)
          }
          success {
            echo 'This section runs when pipeline sucess'
          }
          failure {
            echo 'This section runs when pipeline failed' 
          }
        }
    }
}