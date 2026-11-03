#!/bin/sh
# DLT 메시지 조회·재투입. create-topics.sh 와 같이 Kafka CLI가 있는 곳(브로커 컨테이너 안)에서 실행한다.
#   sh dlt.sh show   <DLT 토픽>   실패 원인 헤더·Key·본문 확인 (offset 기록 안 함, 몇 번이든 실행 가능)
#   sh dlt.sh replay <DLT 토픽>   지난 replay 이후 쌓인 메시지를 원본 토픽(-dlt 제거)으로 다시 발행
#
# replay 는 고정 consumer group "dlt-replay" 로 읽어 어디까지 재투입했는지 기록한다.
# 같은 메시지를 두 번 보내지 않으며, Key 를 유지하므로 원본과 같은 파티션으로 들어간다.
# 재투입 전에 원인(코드 버그, 데이터)을 먼저 고친다. 그대로 넣으면 다시 DLT 로 온다.
# 환경변수: BOOTSTRAP_SERVER(기본 localhost:9092), KAFKA_BIN(기본 /opt/kafka/bin)
set -eu

MODE="${1:-}"; DLT="${2:-}"
case "$MODE:$DLT" in
  show:?* | replay:?*) ;;
  *) echo "사용법: $0 <show|replay> <토픽-dlt>" >&2; exit 2 ;;
esac
case "$DLT" in
  *-dlt) ORIGIN="${DLT%-dlt}" ;;
  *) echo "DLT 토픽은 -dlt 로 끝나야 합니다: $DLT" >&2; exit 2 ;;
esac

BOOTSTRAP_SERVER="${BOOTSTRAP_SERVER:-localhost:9092}"
KAFKA_BIN="${KAFKA_BIN:-/opt/kafka/bin}"
TAB=$(printf '\t')

if [ "$MODE" = "show" ]; then
  # 그룹 없이 처음부터 읽고 offset 은 남기지 않는다
  "$KAFKA_BIN/kafka-console-consumer.sh" --bootstrap-server "$BOOTSTRAP_SERVER" \
    --topic "$DLT" --from-beginning --timeout-ms 10000 \
    --command-property enable.auto.commit=false \
    --formatter-property print.partition=true --formatter-property print.offset=true \
    --formatter-property print.headers=true --formatter-property print.key=true \
    --formatter-property key.separator="$TAB" 2>&1 | grep -v '^\[' || true
  exit 0
fi

# replay: Key<TAB>본문 형태로 읽어 그대로 원본 토픽에 발행 (DLT 헤더는 떼고 보낸다)
# JSON 본문에는 실제 탭 문자가 들어갈 수 없어 구분자로 안전하다
echo "재투입: $DLT -> $ORIGIN"
"$KAFKA_BIN/kafka-console-consumer.sh" --bootstrap-server "$BOOTSTRAP_SERVER" \
  --topic "$DLT" --group dlt-replay --from-beginning --timeout-ms 10000 \
  --formatter-property print.key=true --formatter-property key.separator="$TAB" 2>/dev/null \
  | tee /tmp/dlt-replay.last \
  | "$KAFKA_BIN/kafka-console-producer.sh" --bootstrap-server "$BOOTSTRAP_SERVER" --topic "$ORIGIN" \
      --command-property acks=all \
      --reader-property parse.key=true --reader-property key.separator="$TAB"
echo "재투입 건수: $(wc -l < /tmp/dlt-replay.last)"
