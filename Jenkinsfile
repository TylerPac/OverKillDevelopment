pipeline {
    agent {
        kubernetes {
            yaml '''
                apiVersion: v1
                kind: Pod
                spec:
                  containers:
                    # Kaniko builds by mutating its own container's root filesystem -
                    # a second build in the same container starts on a filesystem the
                    # first build already overwrote, so each image gets its own container.
                    - name: kaniko-backend
                      image: gcr.io/kaniko-project/executor:debug
                      command: ["sleep"]
                      args: ["99d"]
                      securityContext:
                        runAsUser: 0
                        privileged: true
                      volumeMounts:
                        - name: ghcr-docker-config
                          mountPath: /kaniko/.docker
                    - name: kaniko-frontend
                      image: gcr.io/kaniko-project/executor:debug
                      command: ["sleep"]
                      args: ["99d"]
                      securityContext:
                        runAsUser: 0
                        privileged: true
                      volumeMounts:
                        - name: ghcr-docker-config
                          mountPath: /kaniko/.docker
                    - name: git
                      image: alpine/git:2.45.2
                      command: ["sleep"]
                      args: ["99d"]
                      env:
                        - name: GH_TOKEN
                          valueFrom:
                            secretKeyRef:
                              name: github-pat
                              key: token
                  volumes:
                    - name: ghcr-docker-config
                      secret:
                        secretName: ghcr-docker-config
                        items:
                          - key: .dockerconfigjson
                            path: config.json
            '''
        }
    }

    environment {
        BACKEND_IMAGE = "ghcr.io/tylerpac/overkill-backend"
        FRONTEND_IMAGE = "ghcr.io/tylerpac/overkill-frontend"
    }

    stages {
        stage('Checkout') {
            steps {
                container('git') {
                    checkout scm
                }
            }
        }

        stage('Build & Push Backend') {
            steps {
                container('kaniko-backend') {
                    sh '''
                        SHORT_SHA=$(echo "$GIT_COMMIT" | cut -c1-7)
                        /kaniko/executor \
                            --context=dir://$(pwd)/backend \
                            --dockerfile=Dockerfile \
                            --destination=$BACKEND_IMAGE:$SHORT_SHA \
                            --destination=$BACKEND_IMAGE:latest
                    '''
                }
            }
        }

        stage('Build & Push Frontend') {
            steps {
                container('kaniko-frontend') {
                    sh '''
                        SHORT_SHA=$(echo "$GIT_COMMIT" | cut -c1-7)
                        /kaniko/executor \
                            --context=dir://$(pwd)/frontend \
                            --dockerfile=Dockerfile \
                            --destination=$FRONTEND_IMAGE:$SHORT_SHA \
                            --destination=$FRONTEND_IMAGE:latest
                    '''
                }
            }
        }

        stage('Update GitOps repo') {
            steps {
                container('git') {
                    sh '''
                        SHORT_SHA=$(echo "$GIT_COMMIT" | cut -c1-7)

                        git clone https://x-access-token:$GH_TOKEN@github.com/TylerPac/VPSInfrastructure.git infra
                        cd infra/manifests/overkilldevelopment

                        sed -i "s#image: ghcr.io/tylerpac/overkill-backend:.*#image: ghcr.io/tylerpac/overkill-backend:$SHORT_SHA#" backend-deployment.yaml
                        sed -i "s#image: ghcr.io/tylerpac/overkill-frontend:.*#image: ghcr.io/tylerpac/overkill-frontend:$SHORT_SHA#" frontend-deployment.yaml

                        git config user.email "jenkins@tylerpac.dev"
                        git config user.name "Jenkins"

                        if git diff --quiet; then
                            echo "No change to deploy."
                        else
                            git commit -am "Deploy overkilldevelopment $SHORT_SHA"
                            git push
                        fi
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "Built ${BACKEND_IMAGE}:latest and ${FRONTEND_IMAGE}:latest and updated VPSInfrastructure - Argo CD will roll it out."
        }
        failure {
            echo "Pipeline failed."
        }
    }
}
