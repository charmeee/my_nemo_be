# 카카오 OAuth 로그인 시퀀스

배포 환경: nginx 가 `/api/` 를 백엔드로 프록시하고, 그 외는 SPA 로 서빙.

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8080/;   # ← trailing slash 가 /api prefix 를 떼고 백엔드로 전달
}
location / {
    root /release/home/nemo-fe/dist;
    try_files $uri $uri.html $uri/ /index.html;
}
```

## 수정 전 (버그)

`application.yml` 의 `redirect-uri: "{baseUrl}/login/oauth2/code/kakao"` 가 문제.
nginx 가 `/api` 를 떼고 백엔드로 보내기 때문에 백엔드는 자신의 base 가 `https://nemo.mandugohigh.site` (api 없음) 이라고 인식.
결과적으로 카카오에 보내는 redirect_uri 에 `/api` 가 빠져, 카카오 콜백이 SPA 로 떨어진다.

```mermaid
sequenceDiagram
    autonumber
    participant U as 사용자 브라우저
    participant N as nginx
    participant BE as Spring (BE)
    participant K as kauth.kakao.com
    participant FE as 프론트 SPA

    U->>N: GET /api/oauth2/authorization/kakao
    N->>BE: GET /oauth2/authorization/kakao<br/>(nginx 가 /api prefix 제거)
    Note over BE: {baseUrl} 치환 시 X-Forwarded-Host 만<br/>참조 → /api 가 누락된다
    BE-->>U: 302 Location: kauth.kakao.com/oauth/authorize<br/>?redirect_uri=https://nemo.mandugohigh.site/login/oauth2/code/kakao<br/>(❌ /api 빠짐)

    U->>K: 카카오 로그인 + 동의
    K-->>U: 302 Location: https://nemo.mandugohigh.site/login/oauth2/code/kakao?code=...

    U->>N: GET /login/oauth2/code/kakao?code=...
    Note over N: /api/ 매칭 실패 → location / 로 fallback<br/>try_files → /index.html
    N-->>FE: index.html (SPA)
    Note over FE: React Router "No routes matched" ❌
```

## 수정 후 (정상)

`application.yml` 의 `redirect-uri` 에 환경변수 `OAUTH_REDIRECT_BASE_URL` 도입.
prod 에서는 `https://nemo.mandugohigh.site/api` 로 주입 → 카카오에 보내는 redirect_uri 에 `/api` 가 포함된다.

```yaml
redirect-uri: "${OAUTH_REDIRECT_BASE_URL:http://localhost:8080}/login/oauth2/code/kakao"
```

```
# jenkinsController/services/nemo/be.env
OAUTH_REDIRECT_BASE_URL=https://nemo.mandugohigh.site/api
```

```mermaid
sequenceDiagram
    autonumber
    participant U as 사용자 브라우저
    participant N as nginx
    participant BE as Spring (BE)
    participant K as kauth.kakao.com
    participant FE as 프론트 SPA

    U->>N: GET /api/oauth2/authorization/kakao
    N->>BE: GET /oauth2/authorization/kakao
    Note over BE: env OAUTH_REDIRECT_BASE_URL=<br/>https://nemo.mandugohigh.site/api
    BE-->>U: 302 Location: kauth.kakao.com/oauth/authorize<br/>?redirect_uri=https://nemo.mandugohigh.site/api/login/oauth2/code/kakao<br/>(✅ /api 포함)

    U->>K: 카카오 로그인 + 동의
    K-->>U: 302 Location: https://nemo.mandugohigh.site/api/login/oauth2/code/kakao?code=...

    U->>N: GET /api/login/oauth2/code/kakao?code=...
    N->>BE: GET /login/oauth2/code/kakao?code=...<br/>(nginx 가 /api 제거)

    BE->>K: POST /oauth/token<br/>redirect_uri=https://nemo.mandugohigh.site/api/login/oauth2/code/kakao
    Note over K: Console 등록 URI 와 일치 ✅
    K-->>BE: access_token

    BE->>K: GET /v2/user/me
    K-->>BE: 사용자 프로필
    Note over BE: 사용자 upsert + JWT 발급
    BE-->>U: 302 Location: https://nemo.mandugohigh.site/auth/callback?token=...

    U->>N: GET /auth/callback?token=...
    N-->>FE: index.html (SPA)
    Note over FE: AuthCallbackPage<br/>setToken → /albums ✅
```

## 변경 파일

| 파일 | 변경 |
|---|---|
| `nemo-be/src/main/resources/application.yml` | kakao/google `redirect-uri` 의 `{baseUrl}` → `${OAUTH_REDIRECT_BASE_URL:http://localhost:8080}` |
| `jenkinsController/services/nemo/be.env` | `OAUTH_REDIRECT_BASE_URL=https://nemo.mandugohigh.site/api` 추가 |

## 카카오 Developer Console

등록된 Redirect URI: `https://nemo.mandugohigh.site/api/login/oauth2/code/kakao` 그대로 사용.
`https://nemo.mandugohigh.site/login/oauth2/code/kakao` (api 없는 것) 가 등록돼 있다면 이제 미사용이므로 정리 권장.
