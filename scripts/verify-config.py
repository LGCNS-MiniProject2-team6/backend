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
    parser.add_argument('--git-uri', help='HTTPS 원격 또는 file URI. 생략하면 기존 임시 Git 검증')
    parser.add_argument('--git-label', default='main', help='main, slash 없는 tag 또는 commit SHA')
    parser.add_argument('--expected-commit', help='검증할 설정 Git commit SHA')
    parser.add_argument('--expected-version', default='v1', help='외부 저장소의 info.config.version')
    parser.add_argument('--profile', choices=['local', 'dev', 'prod'], default='local')
    args = parser.parse_args()
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
                common.write_text(common.read_text().replace('version: v1', f'version: {marker}'))
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
            config_url = f'http://127.0.0.1:{ports[1]}'
            username, password = 'config-test', secrets.token_urlsafe(32)
            base_env = dict(os.environ, CONFIG_USER=username, CONFIG_PASSWORD=password,
                            CONFIG_GIT_URI=git_uri, CONFIG_GIT_LABEL=label,
                            CONFIG_GIT_USERNAME=git_username, CONFIG_GIT_TOKEN=git_token,
                            CONFIG_SERVER_URL=config_url,
                            EUREKA_URL=f'http://127.0.0.1:{ports[0]}/eureka/',
                            SPRING_PROFILES_ACTIVE=args.profile)
            for i, (name, jar, port) in enumerate(zip(names, jars, ports)):
                sockets[i].close()
                log = (logs_dir / f'{name}.log').open('w')
                logs.append(log)
                env = dict(base_env, SERVER_PORT=str(port))
                process = subprocess.Popen([java, '-Xms64m', '-Xmx256m', '-jar', str(jar)],
                                           cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
                processes.append((name, process))
                if i < 2:
                    msa.wait_until(lambda p=port: request_json(f'http://127.0.0.1:{p}/actuator/health')['status'] == 'UP',
                                   processes, 60, f'{name} health')
                if i == 1:
                    expect_unauthorized(f'{config_url}/auth-service/local/{label}')
                    expect_unauthorized(f'{config_url}/auth-service/local/{label}', username, 'wrong-password')
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
                    print('PASS Config: 4개 서비스 x local/dev/prod 설정 및 Git commit 일치', flush=True)
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
                return all(any(x['status'] == 'UP' and int(x['port']['$']) == port
                               for x in observed.get(name, []))
                           for name, port in zip(names[1:], ports[1:]))

            msa.wait_until(all_registered, processes, 90, 'Config + 4개 서비스 Eureka 등록')
            print('PASS Eureka: Config Server + 도메인 4개 UP 및 포트 일치', flush=True)
            # 잘못된 Config 인증이면 로컬 기본값만으로 기동되지 않아야 한다.
            log = (logs_dir / 'fail-fast.log').open('w')
            logs.append(log)
            env = dict(base_env, CONFIG_PASSWORD='wrong-password', SERVER_PORT='0')
            bad = subprocess.Popen([java, '-Xms64m', '-Xmx256m', '-jar', str(jars[2])],
                                   cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
            processes.append(('fail-fast-auth-service', bad))
            assert bad.wait(timeout=30) != 0, '잘못된 Config 인증으로 기동 성공'
            print('PASS Client: 잘못된 Config 인증 시 기동 실패(fail-fast)', flush=True)
    finally:
        for sock in sockets:
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
        print('검증 프로세스 정리 완료. Config 인증은 임시값이며 Git 인증값은 출력·저장하지 않습니다.', flush=True)


if __name__ == '__main__':
    main()
