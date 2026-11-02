#!/bin/sh
# MariaDB 커스텀 이미지 로컬 검증. Docker만 사용하며 클러스터에 접근하지 않는다.
#   bash database/scripts/verify-local.sh
# 비밀번호는 실행할 때마다 임의 생성하고, 검증이 끝나면 컨테이너를 지운다.
set -eu

export MSYS_NO_PATHCONV=1

ROOT=$(cd "$(dirname "$0")/../.." && { pwd -W 2>/dev/null || pwd; })
IMAGE="${DB_IMAGE:-recu-mariadb:verify}"
NAME="recu-mariadb-verify"

pw() { od -An -N12 -tx1 /dev/urandom | tr -d ' \n'; }
ROOT_PW=$(pw); USER_PW=$(pw); AI_PW=$(pw); BLOG_PW=$(pw)

cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

fail() { echo "실패: $*" >&2; exit 1; }
sql() { # sql <계정> <비밀번호> <쿼리>
  docker exec "$NAME" mariadb -u"$1" -p"$2" -N -B -e "$3"
}

echo "[1] 이미지 빌드"
docker build -q -f "$ROOT/database/Dockerfile" -t "$IMAGE" "$ROOT" >/dev/null

echo "[2] 이미지에 비밀번호 미포함"
docker image inspect "$IMAGE" --format '{{range .Config.Env}}{{println .}}{{end}}' \
  | grep -i 'PASSWORD' >/dev/null && fail "이미지 ENV에 PASSWORD 존재"
echo "    통과"

echo "[3] 빈 볼륨으로 최초 기동"
cleanup
docker run -d --name "$NAME" \
  -e MARIADB_ROOT_PASSWORD="$ROOT_PW" \
  -e USER_DB_PASSWORD="$USER_PW" -e AI_DB_PASSWORD="$AI_PW" -e BLOG_DB_PASSWORD="$BLOG_PW" \
  "$IMAGE" >/dev/null
i=0
until docker exec "$NAME" healthcheck.sh --connect --innodb_initialized >/dev/null 2>&1; do
  i=$((i + 1))
  [ "$i" -le 90 ] || { docker logs --tail 40 "$NAME"; fail "180초 안에 준비되지 않음"; }
  docker inspect -f '{{.State.Running}}' "$NAME" | grep true >/dev/null \
    || { docker logs --tail 40 "$NAME"; fail "컨테이너 종료됨"; }
  sleep 2
done
echo "    준비 완료"

echo "[4] DB 3개, 계정 3개"
dbs=$(sql root "$ROOT_PW" "SHOW DATABASES" | grep -E '^(user|ai|blog)_service$' | wc -l)
[ "$dbs" -eq 3 ] || fail "DB 개수 $dbs"
users=$(sql root "$ROOT_PW" "SELECT COUNT(*) FROM mysql.user WHERE User IN ('user_service','ai_service','blog_service') AND Host='%'")
[ "$users" -eq 3 ] || fail "계정 개수 $users"
echo "    통과"

echo "[5] 서비스 계정 권한 (자기 DB만)"
for svc in user ai blog; do
  eval "p=\$$(echo "$svc" | tr '[:lower:]' '[:upper:]')_PW"
  acct="${svc}_service"
  sql "$acct" "$p" "CREATE TABLE $acct.verify_tmp (id INT); DROP TABLE $acct.verify_tmp" \
    || fail "$acct 자기 DB DDL 불가"
  for other in user ai blog; do
    [ "$other" = "$svc" ] && continue
    sql "$acct" "$p" "SELECT 1 FROM information_schema.schemata WHERE schema_name='${other}_service'" \
      | grep 1 >/dev/null && fail "$acct 가 ${other}_service 를 볼 수 있음"
    sql "$acct" "$p" "USE ${other}_service" >/dev/null 2>&1 && fail "$acct 가 ${other}_service 접근 가능"
  done
  echo "    $acct 통과"
done

echo "[6] blog_service 테이블과 데이터"
for t in company tech_tag blog_post blog_post_section blog_post_tag blog_post_category; do
  n=$(sql blog_service "$BLOG_PW" "SELECT COUNT(*) FROM blog_service.$t") || fail "$t 없음"
  [ "$n" -gt 0 ] || fail "$t 데이터 0건"
  printf '    %-20s %s건\n' "$t" "$n"
done

echo "[7] ai_service.ai_guide"
sql ai_service "$AI_PW" "SELECT COUNT(*) FROM ai_service.ai_guide" >/dev/null || fail "ai_guide 없음"
echo "    통과"

echo "[8] user_service 는 빈 DB (테이블은 user-service JPA가 생성)"
n=$(sql user_service "$USER_PW" "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='user_service'")
echo "    테이블 ${n}개"

echo "검증 완료"
