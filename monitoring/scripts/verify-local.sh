#!/bin/sh
# 관측 환경 로컬 검증. Docker만 사용하며 클러스터에 접근하지 않는다.
#   bash monitoring/scripts/verify-local.sh          검증 후 전부 삭제
#   KEEP=1 bash monitoring/scripts/verify-local.sh   검증 후 그대로 두기 (Grafana 화면 확인용)
#   bash monitoring/scripts/verify-local.sh stop     남겨둔 컨테이너 삭제
#
# MariaDB(이 레포 이미지), Kafka, exporter 2개, Prometheus, Grafana를 한 Docker 네트워크에 띄운다.
# 컨테이너 별칭을 K8s Service 이름과 같게 두어 prometheus.yml 을 그대로 쓴다.
set -eu

export MSYS_NO_PATHCONV=1

ROOT=$(cd "$(dirname "$0")/../.." && { pwd -W 2>/dev/null || pwd; })
NET="recu-mon-verify"
P="recu-mon"
PROM_PORT=19090
GRAFANA_PORT=13000

PROMETHEUS_IMAGE="prom/prometheus:v3.15.1"
GRAFANA_IMAGE="grafana/grafana:13.2.5"
KAFKA_EXPORTER_IMAGE="danielqsj/kafka-exporter:v1.11.0"
MYSQLD_EXPORTER_IMAGE="prom/mysqld-exporter:v0.21.0"
KAFKA_IMAGE="apache/kafka:4.3.2"
DB_IMAGE="recu-mariadb:monitoring-verify"

cleanup() {
  for c in grafana prometheus kafka-exporter mysqld-exporter kafka mariadb; do
    docker rm -f "$P-$c" >/dev/null 2>&1 || true
  done
  docker network rm "$NET" >/dev/null 2>&1 || true
}

if [ "${1:-}" = "stop" ]; then cleanup; echo "삭제 완료"; exit 0; fi
[ "${KEEP:-0}" = "1" ] || trap cleanup EXIT

fail() { echo "실패: $*" >&2; exit 1; }
pw() { od -An -N12 -tx1 /dev/urandom | tr -d ' \n'; }
wait_for() { # wait_for <설명> <최대초> <명령...>
  desc=$1; max=$2; shift 2; i=0
  until "$@" >/dev/null 2>&1; do
    i=$((i + 2)); [ "$i" -le "$max" ] || fail "$desc: ${max}초 안에 준비되지 않음"
    sleep 2
  done
}
prom_value() { # prom_value <PromQL> -> 첫 결과 값
  curl -s -G "http://localhost:$PROM_PORT/api/v1/query" --data-urlencode "query=$1" \
    | grep -oE '"value":\[[^]]*\]' | head -1 | sed -E 's/.*,"([^"]*)"\]/\1/'
}
prom_is() { [ "$(prom_value "$1")" = "$2" ]; }

ROOT_PW=$(pw); EXPORTER_PW=$(pw); GRAFANA_PW=$(pw)

cleanup
docker network create "$NET" >/dev/null

echo "[1] MariaDB 이미지 빌드·기동 (지표 수집 계정 포함)"
docker build -q -f "$ROOT/database/Dockerfile" -t "$DB_IMAGE" "$ROOT" >/dev/null
docker run -d --name "$P-mariadb" --network "$NET" --network-alias mariadb \
  -e MARIADB_ROOT_PASSWORD="$ROOT_PW" -e USER_DB_PASSWORD="$(pw)" -e AI_DB_PASSWORD="$(pw)" \
  -e BLOG_DB_PASSWORD="$(pw)" -e EXPORTER_DB_PASSWORD="$EXPORTER_PW" "$DB_IMAGE" >/dev/null
wait_for "MariaDB" 180 docker exec "$P-mariadb" healthcheck.sh --connect --innodb_initialized

echo "[2] 지표 수집 계정이 서비스 DB를 읽지 못함"
docker exec "$P-mariadb" mariadb -uexporter -p"$EXPORTER_PW" -e "SELECT 1 FROM blog_service.company LIMIT 1" >/dev/null 2>&1 \
  && fail "exporter 계정이 blog_service 조회 가능"
docker exec "$P-mariadb" mariadb -uexporter -p"$EXPORTER_PW" -N -e "SHOW DATABASES" | grep -E '_service$' >/dev/null \
  && fail "exporter 계정에 서비스 DB가 보임"
echo "    통과"

echo "[3] Kafka 기동, 토픽·consumer lag 생성"
set --
while IFS= read -r line; do
  case "$line" in
    '' | \#*) continue ;;
    KAFKA_ADVERTISED_LISTENERS=*) line="KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092" ;;
  esac
  set -- "$@" -e "$line"
done < "$ROOT/kafka/config/broker-dev.env"
docker run -d --name "$P-kafka" --network "$NET" --network-alias kafka --hostname kafka "$@" "$KAFKA_IMAGE" >/dev/null
KBIN=/opt/kafka/bin
wait_for "Kafka" 90 docker exec "$P-kafka" $KBIN/kafka-broker-api-versions.sh --bootstrap-server localhost:9092
docker exec "$P-kafka" $KBIN/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists \
  --topic recu-monitoring-verify --partitions 1 --replication-factor 1 >/dev/null
seq 1 10 | docker exec -i "$P-kafka" $KBIN/kafka-console-producer.sh --bootstrap-server localhost:9092 \
  --topic recu-monitoring-verify >/dev/null
docker exec "$P-kafka" $KBIN/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic recu-monitoring-verify --group verify-group --from-beginning --max-messages 3 >/dev/null 2>&1
echo "    메시지 10건, verify-group 3건 소비 (lag 7 예상)"

echo "[4] exporter, Prometheus, Grafana 기동"
docker run -d --name "$P-kafka-exporter" --network "$NET" --network-alias kafka-exporter \
  "$KAFKA_EXPORTER_IMAGE" --kafka.server=kafka:9092 >/dev/null
docker run -d --name "$P-mysqld-exporter" --network "$NET" --network-alias mysqld-exporter \
  -e MYSQLD_EXPORTER_PASSWORD="$EXPORTER_PW" \
  "$MYSQLD_EXPORTER_IMAGE" --mysqld.address=mariadb:3306 --mysqld.username=exporter >/dev/null
docker run -d --name "$P-prometheus" --network "$NET" --network-alias prometheus -p "$PROM_PORT:9090" \
  -v "$ROOT/monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro" \
  "$PROMETHEUS_IMAGE" --config.file=/etc/prometheus/prometheus.yml \
  --storage.tsdb.path=/prometheus --storage.tsdb.retention.time=7d --storage.tsdb.retention.size=2500MB >/dev/null
docker run -d --name "$P-grafana" --network "$NET" --network-alias grafana -p "$GRAFANA_PORT:3000" \
  -e GF_SECURITY_ADMIN_PASSWORD="$GRAFANA_PW" \
  -v "$ROOT/monitoring/grafana/provisioning/datasources:/etc/grafana/provisioning/datasources:ro" \
  -v "$ROOT/monitoring/grafana/provisioning/dashboards:/etc/grafana/provisioning/dashboards:ro" \
  -v "$ROOT/monitoring/grafana/dashboards:/etc/grafana/dashboards:ro" \
  "$GRAFANA_IMAGE" >/dev/null

echo "[5] Prometheus 수집 확인 (수집 주기 30초라 최대 2분)"
wait_for "Prometheus" 60 curl -sf "http://localhost:$PROM_PORT/-/ready"
wait_for "kafka 대상 UP" 120 prom_is 'up{job="kafka"}' 1
wait_for "mariadb 대상 UP" 120 prom_is 'up{job="mariadb"}' 1
for q in 'kafka_brokers' 'mysql_up'; do
  [ "$(prom_value "$q")" = "1" ] || fail "$q 값이 1이 아님"
  echo "    $q = 1"
done
wait_for "consumer lag" 120 prom_is 'sum(kafka_consumergroup_lag{consumergroup="verify-group"})' 7
echo "    kafka_consumergroup_lag{verify-group} = 7"
q='mysql_global_status_threads_connected'
[ -n "$(prom_value "$q")" ] || fail "$q 없음"
echo "    $q = $(prom_value "$q")"

echo "[6] Grafana datasource·대시보드 자동 등록"
G="http://admin:$GRAFANA_PW@localhost:$GRAFANA_PORT"
wait_for "Grafana" 90 curl -sf "$G/api/health"
curl -sf "$G/api/datasources/uid/prometheus/health" | grep '"status":"OK"' >/dev/null || fail "datasource 연결 실패"
echo "    datasource Prometheus 연결 OK"
wait_for "대시보드" 30 sh -c "[ \$(curl -sf '$G/api/search?tag=recu' | grep -o '\"uid\"' | wc -l) -eq 3 ]"
curl -sf "$G/api/search?tag=recu" | grep -oE '"title":"ReCu[^"]*"' | sed 's/^/    /'

echo "검증 완료"
if [ "${KEEP:-0}" = "1" ]; then
  echo
  echo "Grafana    http://localhost:$GRAFANA_PORT  (admin / $GRAFANA_PW)"
  echo "Prometheus http://localhost:$PROM_PORT/targets"
  echo "종료: bash monitoring/scripts/verify-local.sh stop"
fi
