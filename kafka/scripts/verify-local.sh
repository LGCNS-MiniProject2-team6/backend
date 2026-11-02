#!/bin/sh
# 로컬 Docker로 Kafka 브로커 설정·토픽 스크립트를 검증한다. 클러스터에는 접근하지 않는다.
#   bash kafka/scripts/verify-local.sh
#
# 확인 항목 (이슈 #29 완료 조건)
#   1. broker-dev.env 기반 단일 브로커 KRaft 기동
#   2. 자동 토픽 생성 비활성화
#   3. create-topics.sh 생성 + 재실행 시 동일 상태 유지
#   4. bootstrap 이후 advertised 주소(서비스 DNS 대역)로 produce/consume
#
# K8s Service DNS 대신 컨테이너 hostname "kafka"를 advertised 주소로 써서
# "bootstrap 후 돌려받은 주소로 재접속" 경로를 같은 방식으로 검증한다.
set -eu

# Git Bash가 /opt/... 경로를 Windows 경로로 바꾸지 않게 한다
export MSYS_NO_PATHCONV=1

# Git Bash에서는 docker.exe가 읽을 수 있는 D:/... 형식, Linux/macOS는 일반 경로
ROOT=$(cd "$(dirname "$0")/.." && { pwd -W 2>/dev/null || pwd; })
IMAGE="${KAFKA_IMAGE:-apache/kafka:4.3.2}"
NAME="recu-kafka-verify"
BOOTSTRAP="kafka:9092"
TOPIC="recu-config-bus-local"

cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker rm -f "$NAME" >/dev/null 2>&1 || true

fail() { echo "실패: $*" >&2; exit 1; }
kx() { docker exec -i "$NAME" "$@"; }

echo "== 1. 브로커 기동 ($IMAGE)"
# dev 설정에서 advertised 주소만 로컬 hostname으로 바꿔 -e 인자로 전달한다
# (임시 env 파일은 Git Bash /tmp 경로를 docker.exe가 못 읽으므로 쓰지 않음)
set --
while IFS= read -r line; do
  case "$line" in
    '' | \#*) continue ;;
    KAFKA_ADVERTISED_LISTENERS=*) line="KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://$BOOTSTRAP" ;;
  esac
  set -- "$@" -e "$line"
done < "$ROOT/config/broker-dev.env"
docker run -d --name "$NAME" --hostname kafka "$@" "$IMAGE" >/dev/null

i=0
until kx /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server "$BOOTSTRAP" >/dev/null 2>&1; do
  i=$((i + 1))
  [ "$i" -le 30 ] || { docker logs --tail 40 "$NAME"; fail "60초 안에 브로커가 준비되지 않음"; }
  sleep 2
done
kx cat /var/lib/kafka/data/meta.properties | grep -q '^node.id=1' \
  || fail "KRaft 메타데이터가 데이터 디렉터리에 없음"
echo "OK: KRaft 브로커 준비"

echo "== 2. 자동 토픽 생성 비활성화"
kx /opt/kafka/bin/kafka-configs.sh --bootstrap-server "$BOOTSTRAP" \
  --describe --entity-type brokers --entity-name 1 --all \
  | grep 'auto.create.topics.enable=false' >/dev/null || fail "auto.create.topics.enable이 false가 아님"
echo "OK: auto.create.topics.enable=false"

echo "== 3. 토픽 생성과 재실행"
docker cp "$ROOT/topics" "$NAME:/tmp/topics" >/dev/null
kx env BOOTSTRAP_SERVER="$BOOTSTRAP" sh /tmp/topics/create-topics.sh local
first=$(kx /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --describe --topic "$TOPIC")
kx env BOOTSTRAP_SERVER="$BOOTSTRAP" sh /tmp/topics/create-topics.sh local
second=$(kx /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --describe --topic "$TOPIC")
[ "$first" = "$second" ] || fail "재실행 후 토픽 상태가 달라짐"
printf '%s\n' "$second" | grep -q 'PartitionCount: 1' || fail "파티션 수가 1이 아님"
printf '%s\n' "$second" | grep -q 'retention.ms=86400000' || fail "retention.ms 미적용"
echo "OK: 재실행해도 동일 ($TOPIC, partitions=1, retention.ms=86400000)"

echo "== 4. advertised 주소로 produce/consume"
msg="verify-$(date +%s)"
echo "$msg" | kx sh -c "/opt/kafka/bin/kafka-console-producer.sh --bootstrap-server $BOOTSTRAP --topic $TOPIC"
got=$(kx /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server "$BOOTSTRAP" --topic "$TOPIC" \
  --from-beginning --max-messages 1 --timeout-ms 15000 2>/dev/null || true)
[ "$got" = "$msg" ] || fail "보낸 메시지($msg)와 받은 메시지($got)가 다름"
echo "OK: $msg 송수신"

echo "모든 검증 통과"
