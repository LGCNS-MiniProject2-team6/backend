#!/usr/bin/env python3
"""실제 Eureka/Config/Gateway JAR와 테스트 전용 downstream으로 라우팅·CORS를 검증한다."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import tempfile
import threading
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('msa_verify', ROOT / 'scripts/verify-msa.py')
msa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(msa)
HTTP = urllib.request.build_opener(urllib.request.ProxyHandler({}))
SERVICES = ['auth-service', 'user-service', 'ai-service', 'blog-service']


def request(url, method='GET', headers=None, body=None):
    req = urllib.request.Request(url, data=body, headers=headers or {}, method=method)
    try:
        with HTTP.open(req, timeout=5) as response:
            return response.status, response.headers, response.read()
    except urllib.error.HTTPError as response:
        return response.code, response.headers, response.read()


def json_request(url):
    status, _, body = request(url, headers={'Accept': 'application/json'})
    if status != 200:
        raise urllib.error.URLError(f'HTTP {status}')
    return json.loads(body)


def make_downstream(service):
    calls = []

    class Handler(BaseHTTPRequestHandler):
        def handle_request(self):
            length = int(self.headers.get('Content-Length', '0'))
            payload = {
                'service': service,
                'path': self.path,
                'method': self.command,
                'authorization': self.headers.get('Authorization'),
                'refresh': self.headers.get('Refresh-Token'),
                'userId': self.headers.get('X-User-Id'),
                'body': self.rfile.read(length).decode(),
            }
            calls.append(payload)
            data = json.dumps(payload).encode()
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(data)))
            self.send_header('Authorization', 'Bearer test-response-token')
            self.send_header('Refresh-Token', 'test-response-refresh')
            if self.headers.get('Origin'):
                self.send_header('Access-Control-Allow-Origin', self.headers['Origin'])
                self.send_header('Access-Control-Allow-Credentials', 'true')
            self.end_headers()
            self.wfile.write(data)

        do_GET = do_POST = do_PUT = do_PATCH = do_DELETE = handle_request

        def log_message(self, *_):
            pass

    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    server.daemon_threads = True
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server, thread, calls


def register_stub(eureka_url, service, port):
    instance = {
        'instanceId': f'{service}:gateway-verification',
        'hostName': '127.0.0.1', 'app': service.upper(), 'ipAddr': '127.0.0.1',
        'status': 'UP', 'port': {'$': port, '@enabled': 'true'},
        'securePort': {'$': 443, '@enabled': 'false'},
        'vipAddress': service, 'secureVipAddress': service,
        'dataCenterInfo': {'@class': 'com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo', 'name': 'MyOwn'},
    }
    status, _, _ = request(f'{eureka_url}/apps/{service.upper()}', 'POST',
                           {'Content-Type': 'application/json'}, json.dumps({'instance': instance}).encode())
    assert status == 204, (service, status)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config-repo', type=Path, default=ROOT / 'config-repository-template',
                        help='사용할 YAML 디렉터리. 원본은 수정하지 않고 임시 Git으로 복사')
    parser.add_argument('--profile', choices=['local', 'dev', 'prod'], default='local')
    args = parser.parse_args()
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java')
    if not java:
        raise RuntimeError('Java 17 및 JAVA_HOME 설정 필요')
    names = ['discovery-server', 'config-server', 'api-gateway']
    jars = [ROOT / f'platform/{name}/build/libs/{name}.jar' for name in names]
    for jar in jars:
        if not jar.is_file():
            raise RuntimeError(f'먼저 JAR 빌드: {jar}')
    for name in ['application.yml', 'api-gateway.yml', f'api-gateway-{args.profile}.yml']:
        if not (args.config_repo / name).is_file():
            raise RuntimeError(f'설정 파일 누락: {name}')
    log_dir = Path(tempfile.mkdtemp(prefix='recu-gateway-verify-'))
    print(f'Logs: {log_dir}', flush=True)
    processes, logs, reservations, stubs = [], [], [], []
    with tempfile.TemporaryDirectory(prefix='recu-gateway-git-') as tmp:
        try:
            repo = Path(tmp)
            for source in args.config_repo.glob('*.yml'):
                shutil.copy2(source, repo / source.name)
            for git_args in [['init', '-b', 'main'], ['add', '.'],
                             ['-c', 'user.name=Gateway Verification', '-c', 'user.email=gateway-test@example.invalid',
                              '-c', 'commit.gpgsign=false', '-c', 'core.hooksPath=/dev/null', 'commit', '-m', 'test: gateway config']]:
                subprocess.run(['git', '-C', str(repo), *git_args], check=True, capture_output=True)
            for _ in names:
                sock = socket.socket()
                reservations.append(sock)
                sock.bind(('127.0.0.1', 0))
            ports = [sock.getsockname()[1] for sock in reservations]
            eureka = f'http://127.0.0.1:{ports[0]}/eureka'
            gateway = f'http://127.0.0.1:{ports[2]}'
            origin = 'http://localhost:5173'
            env = dict(os.environ, CONFIG_USER='gateway-verification', CONFIG_PASSWORD=secrets.token_urlsafe(32),
                       CONFIG_GIT_URI=repo.as_uri(), CONFIG_GIT_LABEL='main',
                       CONFIG_GIT_USERNAME='', CONFIG_GIT_TOKEN='',
                       CONFIG_SERVER_URL=f'http://127.0.0.1:{ports[1]}',
                       EUREKA_URL=eureka + '/', FRONTEND_ORIGIN=origin,
                       SPRING_PROFILES_ACTIVE=args.profile)
            for i, (name, jar, port) in enumerate(zip(names, jars, ports)):
                reservations[i].close()
                log = (log_dir / f'{name}.log').open('w')
                logs.append(log)
                runtime_env = dict(env, SERVER_PORT=str(port))
                if name != 'api-gateway':
                    runtime_env.pop('FRONTEND_ORIGIN', None)
                runtime_env.pop('ENCRYPT_KEY', None)
                runtime_env.pop('CONFIG_ADMIN_USER', None)
                runtime_env.pop('CONFIG_ADMIN_PASSWORD', None)
                if name == 'config-server':
                    runtime_env['ENCRYPT_KEY'] = secrets.token_urlsafe(48)
                process = subprocess.Popen([java, '-Xms64m', '-Xmx256m', '-jar', str(jar)], cwd=ROOT,
                                           env=runtime_env, stdout=log, stderr=subprocess.STDOUT)
                processes.append((name, process))
                msa.wait_until(lambda p=port: json_request(f'http://127.0.0.1:{p}/actuator/health')['status'] == 'UP',
                               processes, 60, f'{name} health')
                for probe in ['liveness', 'readiness']:
                    msa.wait_until(lambda p=port, h=probe: json_request(
                        f'http://127.0.0.1:{p}/actuator/health/{h}')['status'] == 'UP',
                        processes, 60, f'{name} {probe}')
                if i == 0:
                    for service in SERVICES:
                        stub = make_downstream(service)
                        stubs.append(stub)
                        register_stub(eureka, service, stub[0].server_port)
            info = json_request(gateway + '/actuator/info')['config']
            assert info['source'] == 'recu-config' and info['service'] == 'api-gateway'
            assert info['profile'] == args.profile

            def gateway_registered():
                apps = json_request(eureka + '/apps')['applications'].get('application', [])
                return any(app['name'] == 'API-GATEWAY' and any(
                    x['status'] == 'UP' and int(x['port']['$']) == ports[2] for x in app['instance']) for app in apps)

            msa.wait_until(gateway_registered, processes, 90, 'Gateway Eureka 등록')
            msa.wait_until(lambda: request(gateway + '/api/auth/probe')[0] == 200,
                           processes, 90, 'Eureka lb:// 라우팅 준비')
            print('PASS Gateway: Config 수신, Eureka UP, 실제 lb:// 인스턴스 연결', flush=True)

            cases = [
                ('POST', '/api/auth/google', 'auth-service'),
                ('POST', '/api/auth/refresh', 'auth-service'),
                ('GET', '/api/profile', 'user-service'),
                ('PUT', '/api/profile', 'user-service'),
                ('GET', '/api/bookmarks', 'user-service'),
                ('DELETE', '/api/bookmarks/42', 'user-service'),
                ('GET', '/api/notifications', 'user-service'),
                ('POST', '/api/posts/42/guide', 'ai-service'),
                ('GET', '/api/posts/42', 'blog-service'),
                ('GET', '/api/companies', 'blog-service'),
                ('GET', '/api/companies/8/posts?page=2&size=10', 'blog-service'),
                ('GET', '/api/tech-tags', 'blog-service'),
                ('POST', '/api/admin/collect', 'blog-service'),
                ('POST', '/api/admin/collect/import', 'blog-service'),
            ]
            for method, path, service in cases:
                body = b'{"verification":true}' if method in ['POST', 'PUT'] else None
                status, _, raw = request(gateway + path, method,
                                          {'Authorization': 'Bearer test-request-token',
                                           'Refresh-Token': 'test-request-refresh', 'X-User-Id': 'forged-user',
                                           'Content-Type': 'application/json'}, body)
                assert status == 200, (path, status)
                received = json.loads(raw)
                assert received['service'] == service, (path, received)
                assert received['path'] == path and received['method'] == method
                assert received['authorization'] == 'Bearer test-request-token'
                assert received['refresh'] == 'test-request-refresh' and received['userId'] is None
                assert received['body'] == (body.decode() if body else '')
            print(f'PASS {len(cases)} routes: 대상/AI 우선순위/경로/쿼리/메서드/본문/토큰 헤더 보존', flush=True)
            before = sum(len(stub[2]) for stub in stubs)
            for path in ['/api/profile', '/api/unmapped-preflight']:
                status, headers, _ = request(gateway + path, 'OPTIONS', {
                    'Origin': origin, 'Access-Control-Request-Method': 'GET',
                    'Access-Control-Request-Headers': 'authorization,refresh-token'})
                assert status == 200 and headers.get('Access-Control-Allow-Origin') == origin, (path, status)
                assert headers.get('Access-Control-Allow-Credentials') == 'true'
            assert before == sum(len(stub[2]) for stub in stubs), 'preflight가 downstream으로 전달됨'
            for method in ['GET', 'OPTIONS']:
                status, _, _ = request(gateway + '/api/profile', method, {
                    'Origin': 'https://untrusted.invalid', 'Access-Control-Request-Method': 'GET'})
                assert status == 403, ('untrusted origin', method, status)
            assert before == sum(len(stub[2]) for stub in stubs), '거부한 Origin이 downstream에 도달함'
            status, headers, _ = request(gateway + '/api/profile', headers={'Origin': origin})
            assert status == 200 and headers.get('Access-Control-Allow-Origin') == origin
            exposed = {x.strip().lower() for x in headers.get('Access-Control-Expose-Headers', '').split(',')}
            assert {'authorization', 'refresh-token'} <= exposed
            assert len(headers.get_all('Access-Control-Allow-Origin')) == 1
            assert len(headers.get_all('Access-Control-Allow-Credentials')) == 1
            assert headers.get('Authorization') == 'Bearer test-response-token'
            assert headers.get('Refresh-Token') == 'test-response-refresh'
            print('PASS CORS: 허용/거부 Origin, 미매칭 OPTIONS, 응답 토큰 헤더 노출', flush=True)
            before = sum(len(stub[2]) for stub in stubs)
            for path in ['/eureka/apps', '/auth-service/local/main', '/encrypt', '/decrypt',
                         '/internal/config-version', '/actuator/env', '/actuator/gateway/routes',
                         '/actuator/busrefresh', '/api-gateway/actuator/health', '/api/not-a-route']:
                status, _, _ = request(gateway + path)
                assert status == 404, (path, status)
            assert before == sum(len(stub[2]) for stub in stubs)
            print('PASS 미등록/관리 경로: 404, downstream 전달 없음', flush=True)
            # 새 Gateway가 중앙 경로 설정 없이 조용히 실행되지 않아야 한다.
            subprocess.run(['git', '-C', str(repo), 'rm', 'api-gateway.yml'], check=True, capture_output=True)
            subprocess.run(['git', '-C', str(repo), '-c', 'user.name=Gateway Verification',
                            '-c', 'user.email=gateway-test@example.invalid', '-c', 'commit.gpgsign=false',
                            '-c', 'core.hooksPath=/dev/null', 'commit', '-m', 'test: missing routes'],
                           check=True, capture_output=True)
            log = (log_dir / 'missing-routes.log').open('w')
            logs.append(log)
            bad = subprocess.Popen([java, '-Xms64m', '-Xmx256m', '-jar', str(jars[2])], cwd=ROOT,
                                   env=dict(env, SERVER_PORT='0'), stdout=log, stderr=subprocess.STDOUT)
            processes.append(('missing-routes', bad))
            assert bad.wait(timeout=30) != 0, '중앙 라우팅 누락 상태로 기동 성공'
            log.flush()
            assert 'Gateway routes are empty' in (log_dir / 'missing-routes.log').read_text()
            print('PASS 중앙 라우팅 누락 시 원인을 명시하고 기동 실패', flush=True)
        finally:
            for sock in reservations:
                sock.close()
            for _, process in reversed(processes):
                if process.poll() is None:
                    process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
            for server, thread, _ in stubs:
                server.shutdown()
                server.server_close()
                thread.join(timeout=3)
            for log in logs:
                log.close()
            print('검증용 JVM/downstream 정리 완료. 실제 비밀값·도메인 비즈니스 로직 미사용.', flush=True)


if __name__ == '__main__':
    main()
