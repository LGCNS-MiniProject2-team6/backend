# ReCu MariaDB: user_service / ai_service / blog_service 를 한 인스턴스에 분리 생성한다.
# 빌드 컨텍스트는 레포 루트 (덤프 파일 사용)
#   docker build -f database/Dockerfile -t recu-mariadb .
# 비밀번호는 이미지에 넣지 않는다. 실행 시 환경변수로 주입한다.
#   MARIADB_ROOT_PASSWORD, USER_DB_PASSWORD, AI_DB_PASSWORD, BLOG_DB_PASSWORD
#   EXPORTER_DB_PASSWORD (선택, mysqld-exporter용)
FROM mariadb:11.4

COPY --chmod=644 database/conf/recu.cnf /etc/mysql/conf.d/recu.cnf
COPY --chmod=644 database/init/ /docker-entrypoint-initdb.d/

# blog 초기 데이터: 덤프에서 데이터 구간(LOCK TABLES ~ UNLOCK TABLES)만 추출한다.
# 테이블 구조는 10-blog-schema.sql 이 기준이다.
COPY mini8-content-dump.sql /tmp/dump.sql
RUN { \
      echo 'USE blog_service;'; \
      echo 'SET NAMES utf8mb4; SET FOREIGN_KEY_CHECKS=0; SET UNIQUE_CHECKS=0;'; \
      awk '/^LOCK TABLES/,/^UNLOCK TABLES/' /tmp/dump.sql; \
      echo 'SET FOREIGN_KEY_CHECKS=1; SET UNIQUE_CHECKS=1;'; \
    } > /docker-entrypoint-initdb.d/11-blog-seed.sql \
 && chmod 644 /docker-entrypoint-initdb.d/11-blog-seed.sql \
 && rm /tmp/dump.sql
