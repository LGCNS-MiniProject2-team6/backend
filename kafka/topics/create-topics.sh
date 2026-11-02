#!/bin/sh
# topics.conf에서 지정 환경의 토픽을 만든다. 여러 번 실행해도 결과가 같다.
#   - 없으면 생성, 있으면 건너뜀
#   - retention.ms는 매번 conf 값으로 맞춘다
#   - 파티션 수가 conf와 다르면 경고만 한다 (자동 증가는 Key 순서를 깨므로 수동 판단)
#
# Kafka CLI가 있는 곳(브로커 컨테이너 안 등)에서 실행한다.
#   sh create-topics.sh dev
# 환경변수
#   BOOTSTRAP_SERVER  기본 localhost:9092
#   KAFKA_BIN         기본 /opt/kafka/bin (apache/kafka 이미지 경로)
#   TOPICS_FILE       기본 이 스크립트 옆 topics.conf
set -eu

ENV_NAME="${1:-}"
if [ -z "$ENV_NAME" ]; then
  echo "사용법: $0 <local|dev|prod>" >&2
  exit 2
fi

BOOTSTRAP_SERVER="${BOOTSTRAP_SERVER:-localhost:9092}"
KAFKA_BIN="${KAFKA_BIN:-/opt/kafka/bin}"
TOPICS_FILE="${TOPICS_FILE:-$(dirname "$0")/topics.conf}"

if [ ! -f "$TOPICS_FILE" ]; then
  echo "토픽 목록 파일이 없습니다: $TOPICS_FILE" >&2
  exit 2
fi

topics() { "$KAFKA_BIN/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP_SERVER" "$@"; }
configs() { "$KAFKA_BIN/kafka-configs.sh" --bootstrap-server "$BOOTSTRAP_SERVER" "$@"; }

# 주석·빈 줄을 뺀 해당 환경 줄
entries=$(grep -v '^[[:space:]]*#' "$TOPICS_FILE" | awk -v e="$ENV_NAME" '$1 == e')
if [ -z "$entries" ]; then
  echo "topics.conf에 '$ENV_NAME' 환경 토픽이 없습니다." >&2
  exit 2
fi

existing=$(topics --list)

printf '%s\n' "$entries" |
while read -r env topic partitions replication retention; do
  if printf '%s\n' "$existing" | grep -qx "$topic"; then
    current=$(topics --describe --topic "$topic" | grep -o 'PartitionCount: *[0-9]*' | grep -o '[0-9]*$')
    if [ "$current" != "$partitions" ]; then
      echo "경고: $topic 파티션 현재 $current, 목록 $partitions (자동 변경하지 않음)" >&2
    fi
    echo "유지: $topic"
  else
    topics --create --if-not-exists --topic "$topic" \
      --partitions "$partitions" --replication-factor "$replication"
    echo "생성: $topic (partitions=$partitions, replication=$replication)"
  fi

  configs --alter --entity-type topics --entity-name "$topic" \
    --add-config "retention.ms=$retention" >/dev/null
  echo "설정: $topic retention.ms=$retention"
done
