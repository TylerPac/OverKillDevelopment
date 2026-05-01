pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Deploy') {
            steps {
                withCredentials([
                    // MySQL (passed to docker-compose)
                    string(credentialsId: 'OKDEV_MYSQL_DATABASE',      variable: 'MYSQL_DATABASE'),
                    string(credentialsId: 'OKDEV_MYSQL_USER',          variable: 'MYSQL_USER'),
                    string(credentialsId: 'OKDEV_MYSQL_PASSWORD',      variable: 'MYSQL_PASSWORD'),
                    string(credentialsId: 'OKDEV_MYSQL_HOST',          variable: 'MYSQL_HOST'),
                    string(credentialsId: 'OKDEV_MYSQL_PORT',          variable: 'MYSQL_PORT'),
                    // Backend env
                    string(credentialsId: 'OKDEV_JWT_SECRET',                   variable: 'JWT_SECRET'),
                    string(credentialsId: 'OKDEV_STRIPE_SECRET_KEY',            variable: 'STRIPE_SECRET_KEY'),
                    string(credentialsId: 'OKDEV_STRIPE_WEBHOOK_SECRET',        variable: 'STRIPE_WEBHOOK_SECRET'),
                    string(credentialsId: 'OKDEV_DISCORD_CLIENT_ID',            variable: 'DISCORD_CLIENT_ID'),
                    string(credentialsId: 'OKDEV_DISCORD_CLIENT_SECRET',        variable: 'DISCORD_CLIENT_SECRET'),
                    string(credentialsId: 'OKDEV_GITHUB_CLIENT_ID',             variable: 'GITHUB_CLIENT_ID'),
                    string(credentialsId: 'OKDEV_GITHUB_CLIENT_SECRET',         variable: 'GITHUB_CLIENT_SECRET'),
                    string(credentialsId: 'OKDEV_GITHUB_BOT_TOKEN',             variable: 'GITHUB_BOT_TOKEN'),
                    string(credentialsId: 'OKDEV_GOOGLE_CLIENT_ID',             variable: 'GOOGLE_CLIENT_ID'),
                    string(credentialsId: 'OKDEV_GOOGLE_CLIENT_SECRET',         variable: 'GOOGLE_CLIENT_SECRET'),
                    string(credentialsId: 'OKDEV_GOOGLE_TEMPLATE_SPREADSHEET_ID', variable: 'GOOGLE_TEMPLATE_SPREADSHEET_ID'),
                    // Encryption key for stored refresh tokens
                    string(credentialsId: 'OKDEV_APP_SECRET',                   variable: 'APP_SECRET'),
                ]) {
                    // Write frontend/.env.production (not a secret, just the relative API path)
                    sh 'echo "VITE_API_BASE_URL=/api" > frontend/.env.production'

                    // Write backend/.env.production from Jenkins secrets
                    sh '''
cat > backend/.env.production <<EOF
DB_URL=jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}
DB_USERNAME=${MYSQL_USER}
DB_PASSWORD=${MYSQL_PASSWORD}
SPRING_PROFILES_ACTIVE=prod
JWT_SECRET=${JWT_SECRET}
APP_SECRET=${APP_SECRET}
STRIPE_SECRET_KEY=${STRIPE_SECRET_KEY}
STRIPE_WEBHOOK_SECRET=${STRIPE_WEBHOOK_SECRET}
APP_AUTH_FRONTEND_BASE_URL=https://overkilldayz.com
APP_AUTH_STEAM_REALM=https://overkilldayz.com
APP_AUTH_STEAM_RETURN_URL=https://overkilldayz.com/api/auth/steam/callback
APP_AUTH_DISCORD_CLIENT_ID=${DISCORD_CLIENT_ID}
APP_AUTH_DISCORD_CLIENT_SECRET=${DISCORD_CLIENT_SECRET}
APP_AUTH_DISCORD_REDIRECT_URI=https://overkilldayz.com/api/auth/discord/callback
APP_AUTH_GITHUB_CLIENT_ID=${GITHUB_CLIENT_ID}
APP_AUTH_GITHUB_CLIENT_SECRET=${GITHUB_CLIENT_SECRET}
APP_AUTH_GITHUB_REDIRECT_URI=https://overkilldayz.com/api/auth/github/callback
APP_GITHUB_BOT_TOKEN=${GITHUB_BOT_TOKEN}
GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}
GOOGLE_CLIENT_SECRET=${GOOGLE_CLIENT_SECRET}
GOOGLE_REDIRECT_URI=https://overkilldayz.com/api/google/callback
GOOGLE_TEMPLATE_SPREADSHEET_ID=${GOOGLE_TEMPLATE_SPREADSHEET_ID}
APP_SHOP_SUCCESS_URL=https://overkilldayz.com
APP_SHOP_CANCEL_URL=https://overkilldayz.com
APP_SHOP_DOWNLOAD_ROOT=downloads
APP_SHOP_GITHUB_REPO_KEYCARD_CRATES=OverKill-Dayz/KeycardCrates
APP_SHOP_GITHUB_REPO_WEAPON_SYSTEM=OverKill-Dayz/DankOpticsPack,OverKill-Dayz/DankWeaponPack,OverKill-Dayz/DankWeaponPack2,OverKill-Dayz/DankWeaponPack3,OverKill-Dayz/DankWeaponScripts,OverKill-Dayz/DankWeaponSounds,OverKill-Dayz/OverKillAmmo
APP_SHOP_GITHUB_REPO_BATTLE_PASS=OverKill-Dayz/BattlePass,OverKill-Dayz/UniversalApi
EOF
'''
                                        // Stop any previous backend/frontend containers (do not touch external mysql)
                                        sh 'docker compose -f docker-compose.yml stop backend frontend || true'
                                        sh 'docker compose -f docker-compose.yml build --no-cache --pull'

                                        // Create a temporary docker-compose override that adds Traefik labels
                                        sh '''
cat > docker-compose.traefik.override.yml <<'YAML'
version: "3.8"
services:
    frontend:
        labels:
            - "traefik.enable=true"
            - "traefik.docker.network=overkill_net"
            - "traefik.http.routers.overkill.rule=Host(\"overkilldayz.com\",\"www.overkilldayz.com\")"
            - "traefik.http.routers.overkill.entrypoints=websecure"
            - "traefik.http.routers.overkill.tls=true"
            - "traefik.http.routers.overkill.tls.certresolver=myresolver"
            - "traefik.http.services.overkill.loadbalancer.server.port=80"
        networks:
            - overkill_net

networks:
    overkill_net:
        external: true
YAML
'''

                                        // If a Traefik container exists, attach it to the project's docker network so it can route
                                        sh '''
for name in $(docker ps --format '{{.Names}}' | grep -i traefik || true); do
    docker network inspect overkill_net >/dev/null 2>&1 || docker network create overkill_net
    docker network connect overkill_net "$name" || true
done
'''

                                        // Start backend/frontend using the override so Traefik labels are present (no MySQL container)
                                        sh 'docker compose -f docker-compose.yml -f docker-compose.traefik.override.yml up -d --no-deps backend frontend'
                }
            }
        }
    }

    post {
        always {
            // Remove the env files and temporary compose override so secrets don't sit on disk
            sh 'rm -f backend/.env.production frontend/.env.production docker-compose.traefik.override.yml'
        }
        success {
            echo '✅ OverKill deployment successful — overkilldayz.com is live.'
        }
        failure {
            echo '❌ OverKill deployment failed. Check console output above.'
        }
    }
}
