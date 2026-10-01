# Nginx 리버스 프록시

Flutter → Nginx → Spring Boot(127.0.0.1:8080) → MariaDB

웹 브라우저 → Nginx → Flutter Web 빌드 파일

기존 API 경로, 쿼리 문자열, 요청 본문과 Authorization 헤더를 전달하는 구성.
인증과 접근 권한은 기존 Spring Security에서 처리. 사용자별 응답 캐시와 프록시의 자동 재요청은 비활성화.

## 로컬 실행

Spring Boot 실행 후 다음 명령 수행. Nginx 실행 파일 경로는 설치 위치에 맞게 지정.

```powershell
$nginxExe = 'C:/path/to/nginx.exe'
$prefix = ((Resolve-Path .).Path.Replace('\', '/')) + '/'
New-Item -ItemType Directory -Force logs | Out-Null
& $nginxExe -p $prefix -c nginx.conf -t
Start-Process -FilePath $nginxExe -ArgumentList @('-p', $prefix, '-c', 'nginx.conf') -WorkingDirectory $prefix -WindowStyle Hidden
```

현재 디렉터리는 이 문서가 있는 `deploy/nginx`. 접속 주소는 http://127.0.0.1:8081.

```powershell
# 설정 검사 후 적용
& $nginxExe -p $prefix -c nginx.conf -t
& $nginxExe -p $prefix -c nginx.conf -s reload
# 종료
& $nginxExe -p $prefix -c nginx.conf -s quit
```

로그는 `logs/access.log`, `logs/error.log`에 저장. 요청 시간과 업스트림 응답 시간을 기록하며 쿼리 문자열, 인증 헤더, 요청 본문은 로그에서 제외.

## EC2 적용

`jiki.ec2.conf`는 EC2용 HTTP 설정 예시. 실제 서버의 기존 Nginx 설정 및 도메인 확인 후 적용.

1. `proxy.conf`를 `/etc/nginx/snippets/jiki-proxy.conf`로 복사.
2. `jiki.ec2.conf`의 API와 웹 도메인을 변경하고 `/etc/nginx/conf.d/jiki.conf`로 복사.
3. Spring Boot에 `SERVER_ADDRESS=127.0.0.1`, `SERVER_PORT=8080`, `SERVER_FORWARD_HEADERS_STRATEGY=native` 설정. 같은 서버의 Nginx만 백엔드에 직접 연결하도록 제한.
4. `sudo nginx -t` 성공 후 `sudo systemctl reload nginx` 실행.
5. 외부 API 주소를 Nginx 주소로 변경하고 보안 그룹에서 8080 직접 접근 차단.

Flutter Web은 `flutter build web` 결과인 `build/web`의 내용을 `/srv/jiki/flutter-web`에 배치.
API 도메인은 Spring Boot로 전달하고, 웹 도메인은 빌드 파일을 제공하는 구성.
모바일 앱은 API 도메인에 연결. Flutter Web의 API 주소와 백엔드 CORS 허용 출처는 실제 API 및 웹 도메인에 맞춰 설정.
로컬 `nginx.conf`는 API 전달용이며, 웹 파일 제공은 `jiki.ec2.conf`에 포함.

이 구성은 Nginx가 직접 요청을 받는 단일 프록시 기준. 앞에 로드 밸런서를 추가할 경우 신뢰할 프록시와 전달 헤더 정책을 함께 조정.
실서비스 인증 요청에는 도메인 인증서를 적용한 HTTPS 설정 필요. 현재 파일에는 인증서 경로나 HTTPS 적용을 가정하지 않음.

참고: [Nginx proxy 모듈](https://nginx.org/en/docs/http/ngx_http_proxy_module.html), [Windows 배포본](https://nginx.org/en/download.html).
