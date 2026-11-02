#!/bin/sh
# IntelliJ에서 띄운 앱이 붙을 로컬 Kafka를 Docker로 실행한다.
#   bash kafka/scripts/run-local.sh        시작 + recu-config-bus-local 토픽 생성
#   bash kafka/scripts/run-local.sh stop   중지·삭제 (데이터도 삭제)
#
# 배포용 broker-dev.env를 그대로 쓰고, advertised 주소만 localhost:9092로 바꾼다.
# 앱 환경변수: KAFKA_BOOTSTRAP_SERVERS=localhost:9092
set -eu

export MSYS_NO_PATHCONV=1

ROOT=$(cd "$(dirname "$0")/.." && { pwd -W 2>/dev/null || pwd; })
IMAGE="${KAFKA_IMAGE:-apache/kafka:4.3.2}"
NAME="recu-kafka-local"

if [ "${1:-}" = "stop" ]; then
  docker rm -f "$NAME" >/dev/null 2>&1 && echo "중지: $NAME" || echo "실행 중인 $NAME 없음"
  exit 0
fi

if docker ps --format '{{.Names}}' | grep -qx "$NAME"; then
  echo "이미 실행 중: $NAME (localhost:9092)"
  exit 0
fi
docker rm -f "$NAME" >/dev/null 2>&1 || true

set --
while IFS= read -r line; do
  case "$line" in
    '' | \#*) continue ;;
    KAFKA_ADVERTISED_LISTENERS=*) line="KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092" ;;
  esac
  set -- "$@" -e "$line"
done < "$ROOT/config/broker-dev.env"
docker run -d --name "$NAME" -p 9092:9092 "$@" "$IMAGE" >/dev/null

echo "브로커 준비 대기..."
i=0
until docker exec "$NAME" /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 >/dev/null 2>&1; do
  i=$((i + 1))
  [ "$i" -le 30 ] || { docker logs --tail 40 "$NAME"; echo "실패: 60초 안에 준비되지 않음" >&2; exit 1; }
  sleep 2
done

docker cp "$ROOT/topics" "$NAME:/tmp/topics" >/dev/null
docker exec "$NAME" sh /tmp/topics/create-topics.sh local

echo "준비 완료: KAFKA_BOOTSTRAP_SERVERS=localhost:9092"
