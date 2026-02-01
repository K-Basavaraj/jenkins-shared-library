//this is function define a function name call()
def call(Map configMap){
    pipeline {
        agent {
            // Jenkins agent (worker node) where this pipeline runs
            label 'AGENT-1'
        }
        options {
            timeout(time: 5, unit: 'MINUTES')  // Fail the build if it runs longer than 5 minutes
            disableConcurrentBuilds() // Prevent multiple builds from running at the same time (or)Prevent parallel runs of same job
        }
        // Parameters shown when triggering the CI job manually
        parameters {
            // Only one control here:
            // deploy = false → only build & push image
            // deploy = true  → build, push, AND trigger CD pipeline
            booleanParam(name: 'deploy', defaultValue: false, description: 'select to deploy or not')
        }
        environment {
            region = 'us-east-1'
            account_id = '688567303455'
            project = configMap.get("project")
            component = configMap.get("component")

            // CI always builds DEV image
            // Higher environments (qa/prod) are handled by CD
            environment = 'dev'
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

            stage('install dependencies'){
                steps {
                    // Install backend dependencies
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
                                // Docker image tag (build-once strategy)
                                def repo = "${account_id}.dkr.ecr.${region}.amazonaws.com/${project}/${environment}/${component}:${env.appVersion}"
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
                    // CD is triggered only when deploy=true
                    expression { params.deploy }
                }
                steps {
                    // CI automatically deploys ONLY to DEV
                    // QA / PROD deployments are triggered manually from CD
                    /*
                    CI → CD CONNECTION HAPPENS HERE
                    We pass TWO VALUES to CD:
                    1. version     : Docker image tag
                    2. ENVIRONMENT : target environment
                    */
                    build job: "../${component}-CD", parameters: [
                    string(name: 'version', value: "${env.appVersion}"),
                    string(name: 'ENVIRONMENT', value: "dev"),
                    ], wait: true   
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