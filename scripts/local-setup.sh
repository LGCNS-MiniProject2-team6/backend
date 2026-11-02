#!/bin/sh
# 로컬 개발 환경 준비: 환경변수 파일 생성 + MariaDB·Kafka 컨테이너 실행
#   bash scripts/local-setup.sh          처음 실행·다시 켜기 (env 파일이 없으면 만든다)
#   bash scripts/local-setup.sh down     MariaDB 중지(데이터 유지) + Kafka 중지·삭제
#   bash scripts/local-setup.sh reset    MariaDB 컨테이너·볼륨 삭제 (다음 실행 때 초기 데이터로 다시 생성)
#
# 만드는 파일 (.gitignore의 .env.* 에 걸려 커밋되지 않는다. 값은 이 PC에서만 쓴다)
#   .env.local          모든 업무 앱·Gateway·Config 공통
#   .env.local.config   Config Server 전용 (ENCRYPT_KEY, recu-config 위치)
#   .env.local.user     user-service DB 접속
#   .env.local.ai       ai-service DB 접속 + GEMINI_API_KEY (직접 채운다)
#   .env.local.blog     blog-service DB 접속
#   .env.local.mariadb  MariaDB 컨테이너 전용 (root·서비스 계정 비밀번호)
# IntelliJ 실행 구성(.run/)이 이 파일들을 읽는다.
set -eu

export MSYS_NO_PATHCONV=1

ROOT=$(cd "$(dirname "$0")/.." && pwd)
WIN_ROOT=$(cd "$ROOT" && { pwd -W 2>/dev/null || pwd; })
DB_IMAGE="${DB_IMAGE:-junwooqkr5302/recu-mariadb:cd63175}"
DB_NAME="recu-mariadb-local"
DB_VOLUME="recu-mariadb-local"
DB_PORT="${DB_PORT:-3306}"

cd "$ROOT"

rand() {
  head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n'
}

case "${1:-up}" in
  down)
    docker stop "$DB_NAME" >/dev/null 2>&1 && echo "중지: $DB_NAME (데이터 유지)" || echo "실행 중인 $DB_NAME 없음"
    sh kafka/scripts/run-local.sh stop
    exit 0
    ;;
  reset)
    docker rm -f "$DB_NAME" >/dev/null 2>&1 || true
    docker volume rm "$DB_VOLUME" >/dev/null 2>&1 || true
    echo "삭제: $DB_NAME 컨테이너·볼륨. 다시 실행하면 초기 데이터로 만든다."
    exit 0
    ;;
  up) ;;
  *)
    echo "사용법: bash scripts/local-setup.sh [up|down|reset]" >&2
    exit 1
    ;;
esac

# 1. 환경변수 파일 (없을 때만 생성. 이미 있으면 그대로 둔다)
if [ ! -f .env.local.mariadb ]; then
  if docker volume inspect "$DB_VOLUME" >/dev/null 2>&1; then
    echo "비밀번호 파일(.env.local.mariadb)이 없는데 기존 DB 볼륨이 있다." >&2
    echo "비밀번호가 맞지 않으므로 먼저 'bash scripts/local-setup.sh reset'을 실행한다." >&2
    exit 1
  fi
  USER_PW=$(rand); AI_PW=$(rand); BLOG_PW=$(rand)
  umask 077
  cat > .env.local.mariadb <<EOF
MARIADB_ROOT_PASSWORD=$(rand)
USER_DB_PASSWORD=$USER_PW
AI_DB_PASSWORD=$AI_PW
BLOG_DB_PASSWORD=$BLOG_PW
EOF
  cat > .env.local.user <<EOF
DB_URL=jdbc:mariadb://localhost:$DB_PORT/user_service
DB_USER=user_service
DB_PASSWORD=$USER_PW
EOF
  cat > .env.local.ai <<EOF
DB_URL=jdbc:mariadb://localhost:$DB_PORT/ai_service
DB_USER=ai_service
DB_PASSWORD=$AI_PW
# 본인 Gemini 키로 바꾼다 (Google AI Studio 발급). 가이드 생성을 안 쓰면 그대로 둬도 기동은 된다
GEMINI_API_KEY=change-me
EOF
  cat > .env.local.blog <<EOF
BLOG_DB_URL=jdbc:mariadb://localhost:$DB_PORT/blog_service
BLOG_DB_USER=blog_service
BLOG_DB_PASSWORD=$BLOG_PW
EOF
  echo "생성: .env.local.mariadb .env.local.user .env.local.ai .env.local.blog"
fi

if [ ! -f .env.local ]; then
  umask 077
  cat > .env.local <<EOF
SPRING_PROFILES_ACTIVE=local
CONFIG_USER=local-config
CONFIG_PASSWORD=$(rand)
BUS_ENABLED=true
INTERNAL_API_TOKEN=$(rand)
EOF
  echo "생성: .env.local"
fi

if [ ! -f .env.local.config ]; then
  CONFIG_REPO="$ROOT/../recu-config"
  if [ -d "$CONFIG_REPO/.git" ]; then
    REPO_PATH=$(cd "$CONFIG_REPO" && { pwd -W 2>/dev/null || pwd; })
    case "$REPO_PATH" in /*) GIT_URI="file://$REPO_PATH" ;; *) GIT_URI="file:///$REPO_PATH" ;; esac
    GIT_NOTE="# backend 옆에 clone한 recu-config를 읽는다. 설정을 바꾸면 그 폴더에서 git pull 한다"
  else
    GIT_URI="https://github.com/LGCNS-MiniProject2-team6/recu-config.git"
    GIT_NOTE="# recu-config를 backend 옆에 clone하지 않았다면 GitHub에서 읽는다. 아래 두 줄의 # 을 지우고 본인 PAT(recu-config Contents: Read-only)를 넣는다"
  fi
  umask 077
  cat > .env.local.config <<EOF
ENCRYPT_KEY=$(rand)
$GIT_NOTE
CONFIG_GIT_URI=$GIT_URI
#CONFIG_GIT_USERNAME=
#CONFIG_GIT_TOKEN=
EOF
  echo "생성: .env.local.config (recu-config: $GIT_URI)"
fi

# 2. MariaDB (우리 팀 이미지: DB 3개·서비스 계정·blog 시드 데이터 포함)
if docker ps --format '{{.Names}}' | grep -qx "$DB_NAME"; then
  echo "이미 실행 중: $DB_NAME (localhost:$DB_PORT)"
elif docker ps -a --format '{{.Names}}' | grep -qx "$DB_NAME"; then
  docker start "$DB_NAME" >/dev/null
  echo "다시 시작: $DB_NAME"
else
  docker run -d --name "$DB_NAME" -p "$DB_PORT:3306" \
    --env-file "$WIN_ROOT/.env.local.mariadb" \
    -v "$DB_VOLUME:/var/lib/mysql" \
    "$DB_IMAGE" >/dev/null
  echo "생성: $DB_NAME ($DB_IMAGE). 처음에는 시드 적재로 1분 안팎 걸린다"
fi

echo "MariaDB 준비 대기..."
i=0
until docker exec "$DB_NAME" healthcheck.sh --connect --innodb_initialized >/dev/null 2>&1; do
  i=$((i + 1))
  [ "$i" -le 90 ] || { docker logs --tail 40 "$DB_NAME"; echo "실패: 3분 안에 준비되지 않음" >&2; exit 1; }
  sleep 2
done
echo "MariaDB 준비 완료: localhost:$DB_PORT"

# 3. Kafka (기존 스크립트: 브로커 + 로컬 토픽)
sh kafka/scripts/run-local.sh

echo
echo "다음: IntelliJ Run 목록에서 '1. Platform' 실행 → Config 기동 확인 → '2. Services' 실행"
grep -q '^GEMINI_API_KEY=change-me' .env.local.ai && echo "참고: .env.local.ai 의 GEMINI_API_KEY 를 본인 키로 바꾼다 (가이드 생성 테스트 시)"
exit 0
