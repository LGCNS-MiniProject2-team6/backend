#!/usr/bin/env python3
"""빌드된 5개 JAR의 health와 실제 Eureka 등록을 검증하고 자신이 띄운 프로세스를 종료한다."""
import argparse
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
SERVICES = ['auth-service', 'user-service', 'ai-service', 'blog-service']
# 로컬 검증에 시스템 HTTP proxy를 경유하지 않는다.
HTTP = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def read_json(url):
    request = urllib.request.Request(url, headers={'Accept': 'application/json'})
    with HTTP.open(request, timeout=2) as response:
        return json.load(response)


def wait_until(check, processes, timeout, description):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        for name, process in processes:
            if process.poll() is not None:
                raise RuntimeError(f'{name} exited with code {process.returncode}')
        try:
            if check():
                return
        except (urllib.error.URLError, TimeoutError, ValueError):
            pass
        time.sleep(0.5)
    raise RuntimeError(f'Timed out: {description}')


def registered(ports):
    applications = read_json(f'http://127.0.0.1:{ports[0]}/eureka/apps')['applications']
    apps = applications.get('application', [])
    if isinstance(apps, dict):
        apps = [apps]
    observed = {}
    for app in apps:
        instances = app.get('instance', [])
        if isinstance(instances, dict):
            instances = [instances]
        observed[app['name'].lower()] = instances
    return all(
        any(instance['status'] == 'UP'
            and int(instance['port']['$']) == port
            for instance in observed.get(name, []))
        for name, port in zip(SERVICES, ports[1:])
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fixed-ports', action='store_true',
                        help='8761/8081/8082/8083/8084로 검증. 기본은 빈 포트 자동 선택')
    args = parser.parse_args()
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java')
    if not java or not Path(java).is_file():
        raise RuntimeError('JDK 17을 설치하고 JAVA_HOME을 설정하세요.')
    names = ['discovery-server'] + SERVICES
    jars = [ROOT / 'platform/discovery-server/build/libs/discovery-server.jar'] + [
        ROOT / f'services/{name}/build/libs/{name}.jar' for name in SERVICES
    ]
    for jar in jars:
        if not jar.is_file():
            raise RuntimeError(f'먼저 MSA JAR를 빌드하세요: {jar}')

    # 이미 실행 중인 프로그램을 중지하거나 재사용하지 않는다.
    reservations = []
    processes = []
    logs = []
    log_dir = Path(tempfile.mkdtemp(prefix='recu-msa-verify-'))
    print(f'Logs: {log_dir}', flush=True)
    try:
        ports = []
        for port in ([8761, 8081, 8082, 8083, 8084] if args.fixed_ports else [0] * 5):
            sock = socket.socket()
            reservations.append(sock)
            sock.bind(('127.0.0.1', port))
            ports.append(sock.getsockname()[1])
        for i, (name, jar, port) in enumerate(zip(names, jars, ports)):
            reservations[i].close()
            log = (log_dir / f'{name}.log').open('w')
            logs.append(log)
            env = dict(os.environ, SERVER_PORT=str(port),
                       EUREKA_URL=f'http://127.0.0.1:{ports[0]}/eureka/')
            process = subprocess.Popen(
                [java, '-Xms64m', '-Xmx256m', '-jar', str(jar)],
                cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT,
            )
            processes.append((name, process))
            if i == 0:
                wait_until(lambda: read_json(f'http://127.0.0.1:{port}/actuator/health')['status'] == 'UP',
                           processes, 60, 'Eureka health')
        for name, port in zip(names, ports):
            for path in ['health', 'health/liveness', 'health/readiness']:
                wait_until(lambda p=port, h=path: read_json(f'http://127.0.0.1:{p}/actuator/{h}')['status'] == 'UP',
                           processes, 60, f'{name} {path}')
            print(f'PASS {name}: health/liveness/readiness UP (port {port})', flush=True)
        wait_until(lambda: registered(ports), processes, 90, '4개 서비스 Eureka UP 등록')
        print('PASS Eureka: AUTH-SERVICE / USER-SERVICE / AI-SERVICE / BLOG-SERVICE UP 및 등록 포트 일치', flush=True)
    finally:
        for sock in reservations:
            sock.close()
        for _, process in processes:
            if process.poll() is None:
                process.terminate()
        for _, process in processes:
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        for log in logs:
            log.close()
        print('검증용 프로세스 정리 완료. 로그는 위 임시 폴더에 보관합니다.', flush=True)


if __name__ == '__main__':
    main()
