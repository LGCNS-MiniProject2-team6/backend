#!/usr/bin/env python3
"""임시 또는 지정 Git과 실제 JAR 6개로 Config 인증/설정 수신/Eureka 등록을 검증한다."""
import argparse
import base64
import importlib.util
import json
import os
import re
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import tempfile
import urllib.error
import urllib.request
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('msa_verify', ROOT / 'scripts/verify-msa.py')
msa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(msa)


def request_json(url, username=None, password=None):
    headers = {'Accept': 'application/json'}
    if username is not None:
        token = base64.b64encode(f'{username}:{password}'.encode()).decode()
        headers['Authorization'] = f'Basic {token}'
    with msa.HTTP.open(urllib.request.Request(url, headers=headers), timeout=3) as response:
        return json.load(response)


def expect_unauthorized(url, username=None, password=None):
    try:
        request_json(url, username, password)
    except urllib.error.HTTPError as error:
        assert error.code == 401, error.code
    else:
        raise AssertionError('인증 없는/잘못된 자격 증명의 설정 조회가 허용됨')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify-bus', action='store_true', help='실제 Kafka로 1회 Bus 갱신 및 무재시작 전파 검증')
    parser.add_argument('--git-uri', help='HTTPS 원격 또는 file URI. 생략하면 기존 임시 Git 검증')
    parser.add_argument('--git-label', default='main', help='main, slash 없는 tag 또는 commit SHA')
    parser.add_argument('--expected-commit', help='검증할 설정 Git commit SHA')
    parser.add_argument('--expected-version', default='v1', help='외부 저장소의 info.config.version')
    parser.add_argument('--profile', choices=['local', 'dev', 'prod'], default='local')
    args = parser.parse_args()
    if args.verify_bus and args.git_uri:
        parser.error("Bus 검증은 원본을 수정하지 않도록 임시 Git에서 실행합니다.")
    if not re.fullmatch(r'[A-Za-z0-9_.-]+', args.git_label):
        parser.error('--git-label은 main, slash 없는 tag 또는 commit SHA를 사용하세요.')
    if args.git_uri:
        uri = urlsplit(args.git_uri)
        if uri.scheme not in ['https', 'file'] or uri.username or uri.password or uri.query or uri.fragment:
            parser.error('--git-uri는 인증정보/쿼리 없는 HTTPS 또는 file URI만 허용합니다.')
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java')
    if not java:
        raise RuntimeError('Java 17 및 JAVA_HOME 설정 필요')
    services = msa.SERVICES
    names = ['discovery-server', 'config-server'] + services
    jars = [ROOT / f'platform/{name}/build/libs/{name}.jar' for name in names[:2]] + [
        ROOT / f'services/{name}/build/libs/{name}.jar' for name in services
    ]
    if args.verify_bus:
        names.append('api-gateway')
        jars.append(ROOT / 'platform/api-gateway/build/libs/api-gateway.jar')
    for jar in jars:
        if not jar.is_file():
            raise RuntimeError(f'먼저 JAR 빌드 필요: {jar}')
    logs_dir = Path(tempfile.mkdtemp(prefix='recu-config-verify-'))
    print(f'Logs: {logs_dir}', flush=True)
    processes, logs, sockets = [], [], []
    try:
        with tempfile.TemporaryDirectory(prefix='recu-config-git-') as tmp:
            if args.git_uri:
                git_uri = args.git_uri
                if urlsplit(git_uri).scheme == 'file':
                    # JGit file backend의 checkout이 개발자의 작업 브랜치를 바꾸지 않도록 복제본만 사용한다.
                    snapshot = Path(tmp) / 'snapshot'
                    subprocess.run(['git', 'clone', '--no-hardlinks', git_uri, str(snapshot)],
                                   check=True, capture_output=True)
                    git_uri = snapshot.as_uri()
                label = args.git_label
                commit = args.expected_commit
                marker = args.expected_version
                git_username = os.environ.get('CONFIG_GIT_USERNAME', '')
                git_token = os.environ.get('CONFIG_GIT_TOKEN', '')
            else:
                repo = Path(tmp)
                for source in (ROOT / 'config-repository-template').glob('*.yml'):
                    shutil.copy2(source, repo / source.name)
                # 임시 저장소에만 존재하는 값으로 실제 Config 수신을 검증한다.
                marker = 'verify-' + secrets.token_hex(8)
                common = repo / 'application.yml'
                common.write_text(common.read_text().replace('    version: v1', f'    version: {marker}'))
                for git_args in [['init', '-b', 'main'], ['add', '.'],
                                 ['-c', 'user.name=Config Verification', '-c', 'user.email=config-test@example.invalid',
                                  '-c', 'commit.gpgsign=false', '-c', 'core.hooksPath=/dev/null',
                                  'commit', '-m', 'test: config fixture']]:
                    subprocess.run(['git', '-C', str(repo), *git_args], check=True, capture_output=True)
                commit = subprocess.check_output(['git', '-C', str(repo), 'rev-parse', 'HEAD'], text=True).strip()
                git_uri, label = repo.as_uri(), 'main'
                git_username, git_token = '', ''
            for _ in names:
                sock = socket.socket()
                sockets.append(sock)
                sock.bind(('127.0.0.1', 0))
            ports = [sock.getsockname()[1] for sock in sockets]
            broker = None
            topic = 'recu-config-bus-test-' + secrets.token_hex(6)
            if args.verify_bus:
                cp_file = ROOT / 'platform/config-server/build/bus-test-classpath.txt'
                if not cp_file.is_file():
                    raise RuntimeError('먼저 :platform:config-server:writeBusTestClasspath 실행 필요')
                broker_log = (logs_dir / 'kafka.log').open('w')
                logs.append(broker_log)
                kafka = subprocess.Popen([java, '-Xms128m', '-Xmx512m', '-cp', cp_file.read_text(),
                                          'com.recu.platform.config.support.LocalKafkaBroker', topic],
                                         cwd=ROOT, stdout=broker_log, stderr=subprocess.STDOUT)
                processes.append(('test-kafka', kafka))
                def broker_ready():
                    nonlocal broker
                    match = re.search(r'KAFKA_READY=(\S+)', (logs_dir / 'kafka.log').read_text())
                    if match:
                        broker = match.group(1)
                    return broker is not None
                msa.wait_until(broker_ready, processes, 90, '실제 Kafka 브로커 준비')
                print('PASS Bus: 실제 테스트 Kafka 브로커 및 환경별 독립 토픽 준비', flush=True)
            config_url = f'http://127.0.0.1:{ports[1]}'
            username, password = 'config-test', secrets.token_urlsafe(32)
            admin, admin_password = 'config-admin-test', secrets.token_urlsafe(32)
            base_env = dict(os.environ, CONFIG_USER=username, CONFIG_PASSWORD=password,
                            CONFIG_GIT_URI=git_uri, CONFIG_GIT_LABEL=label,
                            CONFIG_GIT_USERNAME=git_username, CONFIG_GIT_TOKEN=git_token,
                            CONFIG_SERVER_URL=config_url, FRONTEND_ORIGIN='http://localhost:5173',
                            EUREKA_URL=f'http://127.0.0.1:{ports[0]}/eureka/',
                            SPRING_PROFILES_ACTIVE=args.profile,
                            BUS_ENABLED='true' if args.verify_bus else 'false',
                            BUS_DESTINATION=topic, KAFKA_BOOTSTRAP_SERVERS=broker or 'localhost:9092')
            for i, (name, jar, port) in enumerate(zip(names, jars, ports)):
                sockets[i].close()
                log = (logs_dir / f'{name}.log').open('w')
                logs.append(log)
                env = dict(base_env, SERVER_PORT=str(port), BUS_INSTANCE_ID=f'{name}:verification:{port}')
                env.pop('CONFIG_ADMIN_USER', None)
                env.pop('CONFIG_ADMIN_PASSWORD', None)
                if name == 'config-server' and args.verify_bus:
                    env['CONFIG_ADMIN_USER'] = admin
                    env['CONFIG_ADMIN_PASSWORD'] = admin_password
                process = subprocess.Popen([java, '-Xms64m', '-Xmx256m', '-jar', str(jar)],
                                           cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
                processes.append((name, process))
                if i < 2:
                    msa.wait_until(lambda p=port: request_json(f'http://127.0.0.1:{p}/actuator/health')['status'] == 'UP',
                                   processes, 60, f'{name} health')
                if i == 1:
                    expect_unauthorized(f'{config_url}/user-service/local/{label}')
                    expect_unauthorized(f'{config_url}/user-service/local/{label}', username, 'wrong-password')
                    print('PASS Config: 무인증/잘못된 인증 조회 401', flush=True)
                    for profile in ['local', 'dev', 'prod']:
                        for service in services:
                            payload = request_json(f'{config_url}/{service}/{profile}/{label}', username, password)
                            effective = {}
                            for source in reversed(payload['propertySources']):
                                effective.update(source['source'])
                            if commit is None:
                                commit = payload['version']
                            assert payload['version'] == commit
                            assert effective['info.config.profile'] == profile
                            assert effective['info.config.service'] == service
                            assert effective['info.config.version'] == marker
                    print('PASS Config: 3개 서비스 x local/dev/prod 설정 및 Git commit 일치', flush=True)
            for name, port in zip(names, ports):
                for path in ['health', 'health/liveness', 'health/readiness']:
                    msa.wait_until(lambda p=port, h=path: request_json(f'http://127.0.0.1:{p}/actuator/{h}')['status'] == 'UP',
                                   processes, 60, f'{name} {path}')
                if name in services:
                    actual = request_json(f'http://127.0.0.1:{port}/actuator/info')['config']
                    assert actual == {'source': 'recu-config', 'version': marker, 'profile': args.profile, 'service': name}, actual
                print(f'PASS {name}: Health/Probe 및 중앙 설정 수신', flush=True)

            def all_registered():
                apps = request_json(f'http://127.0.0.1:{ports[0]}/eureka/apps')['applications'].get('application', [])
                observed = {}
                for app in apps:
                    instances = app.get('instance', [])
                    if isinstance(instances, dict):
                        instances = [instances]
                    observed[app['name'].lower()] = instances
                return 'auth-service' not in observed and all(any(x['status'] == 'UP' and int(x['port']['$']) == port
                               for x in observed.get(name, []))
                           for name, port in zip(names[1:], ports[1:]))

            msa.wait_until(all_registered, processes, 90, 'Config + 3개 서비스 Eureka 등록')
            print('PASS Eureka: Config Server + 도메인 3개 UP 및 포트 일치', flush=True)
            if args.verify_bus:
                participants = list(zip(names[1:], ports[1:]))
                msa.wait_until(lambda: all('partitions assigned' in (logs_dir / f'{name}.log').read_text()
                                          for name, _ in participants), processes, 90, '모든 Bus 구독자 partition 할당')
                recipients = list(zip(names[2:], ports[2:]))
                before = {name: request_json(f'http://127.0.0.1:{port}/actuator/info')['refresh']
                          for name, port in recipients}
                assert all(value['version'] == 'v1' for value in before.values())
                assert len({value['busId'] for value in before.values()}) == len(before)
                common = repo / 'application.yml'
                common.write_text(common.read_text().replace('config-version: v1', 'config-version: v2'))
                subprocess.run(['git', '-C', str(repo), 'add', 'application.yml'], check=True, capture_output=True)
                subprocess.run(['git', '-C', str(repo), '-c', 'user.name=Bus Verification',
                                '-c', 'user.email=bus-test@example.invalid', '-c', 'commit.gpgsign=false',
                                '-c', 'core.hooksPath=/dev/null', 'commit', '-m', 'test: v2 config'],
                               check=True, capture_output=True)
                assert all(request_json(f'http://127.0.0.1:{port}/actuator/info')['refresh']['version'] == 'v1'
                           for _, port in recipients), 'Bus 호출 전 설정이 바뀜'
                def refresh_call(user=None, secret=None, method='POST', url=config_url + '/actuator/busrefresh'):
                    headers = {'Content-Type': 'application/json'}
                    if user is not None:
                        headers['Authorization'] = 'Basic ' + base64.b64encode(f'{user}:{secret}'.encode()).decode()
                    req = urllib.request.Request(url, data=b'' if method == 'POST' else None, headers=headers, method=method)
                    try:
                        with msa.HTTP.open(req, timeout=10) as response:
                            return response.status
                    except urllib.error.HTTPError as error:
                        return error.code
                assert refresh_call() == 401
                assert refresh_call(username, password) == 403
                assert refresh_call(admin, 'wrong-password') == 401
                assert refresh_call(admin, admin_password, method='GET') == 403
                publish_status = refresh_call(admin, admin_password)
                assert publish_status == 204, f'Bus publish HTTP {publish_status}'
                for name, port in recipients:
                    msa.wait_until(lambda p=port: request_json(f'http://127.0.0.1:{p}/actuator/info')['refresh']['version'] == 'v2',
                                   processes, 90, f'{name} Bus v2 전파')
                    after = request_json(f'http://127.0.0.1:{port}/actuator/info')['refresh']
                    assert all(after[key] == before[name][key] for key in ['pid', 'startTime', 'busId'])
                    print(f'PASS Bus {name}: v1 -> v2, PID/startTime/Bus ID 유지', flush=True)
                gateway_url = f'http://127.0.0.1:{ports[-1]}'
                for path in ['/actuator/busrefresh', '/api/actuator/busrefresh', '/internal/config-version', '/api/internal/config-version']:
                    assert refresh_call(url=gateway_url + path) == 404, path
                print('PASS Gateway: Bus 관리 API 및 내부 확인 경로 업무 라우트 미노출', flush=True)
                print('PASS Bus: 관리자 1회 호출로 세 서비스+Gateway 무재시작 갱신, 무인증 401/읽기 계정 403', flush=True)
            # 잘못된 Config 인증이면 로컬 기본값만으로 기동되지 않아야 한다.
            log = (logs_dir / 'fail-fast.log').open('w')
            logs.append(log)
            env = dict(base_env, CONFIG_PASSWORD='wrong-password', SERVER_PORT='0')
            bad = subprocess.Popen([java, '-Xms64m', '-Xmx256m', '-jar', str(jars[2])],
                                   cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
            processes.append(('fail-fast-user-service', bad))
            assert bad.wait(timeout=30) != 0, '잘못된 Config 인증으로 기동 성공'
            print('PASS Client: 잘못된 Config 인증 시 기동 실패(fail-fast)', flush=True)
    finally:
        for sock in sockets:
            sock.close()
        for _, process in reversed(processes):
            if process.poll() is None:
                process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        for log in logs:
            log.close()
        print('검증 프로세스 정리 완료. Config 인증은 임시값이며 Git 인증값은 출력·저장하지 않습니다.', flush=True)


if __name__ == '__main__':
    main()
