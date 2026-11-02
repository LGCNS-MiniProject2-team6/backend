#!/bin/bash
# 빈 데이터 디렉터리로 최초 기동할 때 docker-entrypoint.sh 가 source 로 1회 실행한다.
# 서비스마다 DB 1개, 계정 1개를 만들고 자기 DB에만 권한을 준다.
#   user -> user_service, ai -> ai_service, blog -> blog_service (계정명 = DB명)

for svc in user ai blog; do
  var="$(echo "$svc" | tr '[:lower:]' '[:upper:]')_DB_PASSWORD"
  pass="${!var:-}"
  [ -n "$pass" ] || mysql_error "$var 환경변수가 비어 있습니다"

  db="${svc}_service"
  pass_esc="$(docker_sql_escape_string_literal "$pass")"
  mysql_note "DB·계정 생성: $db"
  docker_process_sql --database=mysql <<-EOSQL
	CREATE DATABASE IF NOT EXISTS \`$db\` CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
	CREATE USER IF NOT EXISTS '$db'@'%' IDENTIFIED BY '$pass_esc';
	GRANT ALL PRIVILEGES ON \`$db\`.* TO '$db'@'%';
	EOSQL
done

# 지표 수집 계정 (mysqld-exporter). EXPORTER_DB_PASSWORD가 있을 때만 만든다.
# 서버 상태만 조회하고 서비스 DB 데이터는 읽지 못한다. 모니터링을 띄우지 않는 환경(prod)은 생략 가능.
if [ -n "${EXPORTER_DB_PASSWORD:-}" ]; then
  exporter_esc="$(docker_sql_escape_string_literal "$EXPORTER_DB_PASSWORD")"
  mysql_note "지표 수집 계정 생성: exporter"
  docker_process_sql --database=mysql <<-EOSQL
	CREATE USER IF NOT EXISTS 'exporter'@'%' IDENTIFIED BY '$exporter_esc' WITH MAX_USER_CONNECTIONS 3;
	GRANT PROCESS, BINLOG MONITOR, SLAVE MONITOR ON *.* TO 'exporter'@'%';
	EOSQL
else
  mysql_note "EXPORTER_DB_PASSWORD 없음: 지표 수집 계정 생략"
fi
