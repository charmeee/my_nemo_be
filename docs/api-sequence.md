# API 시퀀스 다이어그램

앨범 생성 → 페이지 생성 → 객체(element) 추가까지의 주요 백엔드 흐름.

대상 API
- 앨범 에디터 진입 — REST 2번 + WebSocket 핸드셰이크/hydration
- `POST /albums` — 앨범 생성
- `POST /albums/{albumId}/pages` — 페이지 생성
- WebSocket `/sync/excalidraw/{albumId}` `push` 메시지 — 캔버스 객체 추가/수정

---

## 0. 앨범 에디터 진입 (`/albums/{albumId}` 페이지 로드)

`AlbumEditorPage` 가 마운트되면 REST 2번 + WebSocket 1개가 거의 동시에 시작된다.
WS hydration 은 클라이언트가 들고 있던 `lastClockByPage` 유무에 따라 **full** (최초 진입) 또는 **delta** (재진입/새로고침) 로 분기된다.

```mermaid
sequenceDiagram
    autonumber
    participant U as 사용자
    participant FE as AlbumEditorPage<br/>+ useExcalidrawSync
    participant REST as REST API<br/>(Spring)
    participant WSI as JwtHandshake<br/>Interceptor
    participant H as ExcalidrawSyncHandler
    participant RC as MemberRoleCacheService<br/>(Redis)
    participant PDS as PageDocumentStore<br/>(mem→Redis→DB)
    participant RM as RoomManager
    participant OTH as 같은 방의<br/>다른 세션
    participant FS as /files/** (JWT)

    U->>FE: /albums/{albumId} 진입

    Note over FE,REST: ① REST: 앨범 메타 + 페이지 목록 (병렬)
    par useQuery(album)
        FE->>REST: GET /albums/{albumId}<br/>Authorization: Bearer JWT
        REST-->>FE: 200 { name, isLocked, myRole, ... }
    and useEffect(pages)
        FE->>REST: GET /albums/{albumId}/pages
        REST-->>FE: 200 PageListResponse[]
        Note over FE: currentPageId = pages[0].pageId
    end

    Note over FE: ② getToken — exp 60초 전이면 갱신
    alt 토큰 만료 임박
        FE->>REST: POST /auth/refresh (cookie)
        REST-->>FE: { accessToken (new) }
    end

    Note over FE,H: ③ WebSocket 핸드셰이크
    FE->>WSI: GET /sync/excalidraw/{albumId}<br/>Upgrade: websocket
    Note over WSI: SecurityConfig 상 permitAll<br/>실제 인증은 connect 메시지에서
    WSI-->>FE: 101 Switching Protocols
    H->>H: afterConnectionEstablished<br/>(keep-alive 30s 시작)

    Note over FE,H: ④ connect 메시지 (토큰은 URL 이 아닌 본문)
    FE->>H: { type:"connect", token,<br/>lastClockByPage, currentPageId, clientId }

    H->>H: JWT 검증 → userId / userName
    alt 게스트(guest:{albumId})
        Note over H: tokenAlbumId == albumId 확인
    else 일반 사용자
        H->>REST: AlbumMemberRepository<br/>.findActiveByAlbumIdAndUserId
        alt 멤버 아님
            H-->>FE: { type:"error", error:"not-member" }
            H-->>FE: close
        end
    end

    H->>RC: getRole(albumId, userId)
    RC-->>H: AlbumRole (VIEWER → isReadonly=true)

    Note over H,PDS: ⑤ hydration — full or delta
    alt lastClockByPage 비어있음 → full
        loop 모든 페이지
            H->>PDS: loadElements(pageId)
            PDS-->>H: elements JSON<br/>(currentPageId 만 본문, 나머진 [])
        end
        H-->>FE: { type:"connected", hydrationType:"full",<br/>isReadonly, pages:[...], roomMembers, files }
    else 클라이언트 clock 보유 → delta
        loop 서버 clock > 클라이언트 clock 인 페이지만
            H->>PDS: loadElements(pageId)
        end
        H-->>FE: { type:"connected", hydrationType:"delta",<br/>isReadonly, deltaByPage:{...}, roomMembers, files }
    end

    Note over H,OTH: ⑥ 다른 세션에 입장 알림 + room 등록
    H->>OTH: { type:"user_joined", userId, userName }
    H->>RM: join(albumId, session)

    Note over FE,FS: ⑦ files 매핑 → 실제 바이너리 다운로드
    loop msg.files 의 (fileId → url) 마다
        FE->>FS: GET {url} (Authorization: Bearer JWT)
        FS-->>FE: blob (image/*)
        Note over FE: blob → dataURL → excalidrawApi.addFiles<br/>(ShapeCache 무효화 → placeholder 해제)
    end

    Note over FE,H: ⑧ 이후 정상 운영 동안 오가는 메시지
    par 30초 keep-alive
        H-->>FE: { type:"pong" }
    and 다른 사람의 커서/선택
        OTH->>H: { type:"presence", pageId, cursor, selectedIds }
        H-->>FE: { type:"presence", presence:{ userId, userName, ... } }
    and 다른 사람의 그림 변경
        OTH->>H: { type:"push", ... }
        H-->>FE: { type:"patch", pageId, serverClock, elements(diff), deletedIds }
    and 다른 사람의 페이지 추가/삭제
        H-->>FE: { type:"page_event", event, pageId, pageName, pageOrder }
    and 추방/잠금/권한강등
        H-->>FE: { type:"force-close", reason:"kicked|role-downgraded|album-locked" }
    end
```

핵심 포인트
- **REST 2개 + WS 1개가 동시에 출발**한다. `currentPageId` 가 REST 응답으로 결정되기 전에 WS `connect` 가 먼저 갈 수 있어서, 서버는 `currentPageId` 가 null/빈값/"null" 이면 첫 페이지로 fallback (`ExcalidrawSyncHandler.java:239`).
- **토큰은 URL query 가 아니라 connect 메시지 본문**으로 전달 — nginx 로그/프록시 노출 방지.
- **hydration 분기**:
  - 최초 진입 → `full`: 모든 페이지의 메타를 보내되 본문(elements) 은 `currentPageId` 만, 나머지는 빈 배열. 전환 시 REST `/pages/{pageId}/elements` 로 재요청한다.
  - 새로고침/재연결 → `delta`: 서버 clock > 클라이언트 clock 인 페이지만 elements 전송. 대역폭 절감.
- **`isReadonly`** = VIEWER 거나 role 캐시 미스 — 클라이언트는 이걸로 캔버스를 readonly 로 잠근다.
- **`roomMembers`** = 지금 방에 접속해 있는 사용자 목록. 초기 우상단 participant pill 표시에 사용.
- **`files`** = `(fileId → url)` 매핑만. 실제 이미지는 클라이언트가 JWT 헤더 달고 `GET /files/**` 로 따로 받는다 (B-SEC-05).
- **room join 순서**: 인증 → `connected` 응답 → `user_joined` broadcast → `RoomManager.join`. join 을 마지막에 두는 이유는 인증 안 된 세션이 broadcast 를 받지 못하게 하기 위함.
- **재연결**: WS `onclose` 시 exponential backoff (3s → 6s → 12s → … 최대 60s). 재연결 시 `lastClockByPage` 를 들고 가므로 → delta hydration.

---

## 1. 앨범 추가 (`POST /albums`)

생성 트랜잭션 안에서 `AlbumCreatedEvent` 를 publish 하고, `BEFORE_COMMIT` 페이즈의 리스너가 같은 트랜잭션에서 초대 링크와 기본 페이지 1개를 함께 생성한다. (커밋 전에 실패하면 앨범 자체도 롤백)

```mermaid
sequenceDiagram
    autonumber
    participant FE as Frontend
    participant SEC as SecurityFilter<br/>(JWT)
    participant CTL as AlbumController
    participant SVC as AlbumService
    participant ARP as AlbumRepository
    participant AMR as AlbumMemberRepository
    participant EP as ApplicationEventPublisher
    participant LIS as AlbumCreatedEventListener
    participant IRP as InviteLinkRepository
    participant PRP as ExcalidrawPageRepository
    participant DB as PostgreSQL

    FE->>SEC: POST /albums<br/>Authorization: Bearer JWT<br/>{ name, coverImage }
    SEC->>SEC: JWT 검증 → userId 주입
    SEC->>CTL: createAlbum(userId, req)

    rect rgb(240, 248, 255)
    Note over CTL,DB: @Transactional 시작
    CTL->>SVC: createAlbum(userId, req)
    SVC->>ARP: save(Album.create(name, cover, userId))
    ARP->>DB: INSERT albums
    SVC->>AMR: save(AlbumMember ADMIN/ACTIVE)
    AMR->>DB: INSERT album_members
    SVC->>EP: publishEvent(AlbumCreatedEvent)

    Note over EP,LIS: BEFORE_COMMIT 페이즈
    EP->>LIS: onAlbumCreated(event)
    LIS->>IRP: save(InviteLink EDITOR, code=16자리)
    IRP->>DB: INSERT invite_links
    LIS->>PRP: save(ExcalidrawPage "페이지 1", order=1)
    PRP->>DB: INSERT excalidraw_pages

    Note over CTL,DB: COMMIT
    end

    SVC-->>CTL: AlbumResponse
    CTL-->>FE: 200 ApiResponse[AlbumResponse]
```

핵심 포인트
- ADMIN 멤버 등록은 `AlbumService` 가, 초대 링크 + 기본 페이지는 리스너가 책임 분리.
- `BEFORE_COMMIT` 으로 같은 트랜잭션 안에 묶여 있어, 둘 중 하나라도 실패하면 앨범 자체가 롤백된다.
- 응답 시점에는 이미 기본 페이지 1개와 초대 링크 1개가 DB 에 존재.

---

## 2. 페이지 추가 (`POST /albums/{albumId}/pages`)

권한 검증 → 최대 30개 제한 → DB 저장 → 같은 방의 다른 세션에 `page_event(added)` 브로드캐스트 → 모든 active 멤버에게 SSE 알림 발송.

```mermaid
sequenceDiagram
    autonumber
    participant FE as Frontend
    participant SEC as SecurityFilter
    participant CTL as ExcalidrawPageController
    participant SVC as ExcalidrawPageService
    participant AMR as AlbumMemberRepository
    participant PRP as ExcalidrawPageRepository
    participant ARP as AlbumRepository
    participant RM as RoomManager
    participant WS as 다른 WebSocket<br/>세션들
    participant NS as NotificationService
    participant DB as PostgreSQL

    FE->>SEC: POST /albums/{albumId}/pages<br/>{ name? }
    SEC->>CTL: createPage(albumId, userId, req)

    rect rgb(240, 248, 255)
    Note over CTL,DB: @Transactional 시작
    CTL->>SVC: createPage(...)
    SVC->>AMR: findActiveByAlbumIdAndUserId
    AMR-->>SVC: AlbumMember
    Note over SVC: VIEWER 차단<br/>(EDITOR/ADMIN 만 허용)

    SVC->>PRP: countByAlbumIdAndDeletedAtIsNull
    Note over SVC: count 가 30 이상이면<br/>ALBUM_PAGE_LIMIT_EXCEEDED

    SVC->>ARP: findById(albumId)
    ARP-->>SVC: Album

    SVC->>PRP: save(ExcalidrawPage.create(album, name, order))
    PRP->>DB: INSERT excalidraw_pages

    Note over SVC,WS: 같은 방의 다른 세션에 알리기
    SVC->>RM: getSessions(albumId)
    RM-->>SVC: Set[WebSocketSession]
    loop 각 세션
        SVC->>WS: TextMessage(type=page_event,<br/>event=added, pageId, name, order)
    end

    Note over SVC,NS: N-NOTIF-05: 모든 active 멤버 알림
    SVC->>AMR: findByAlbumIdAndStatus(ACTIVE)
    AMR-->>SVC: List[AlbumMember]
    loop 각 멤버
        SVC->>NS: send(userId, NEW_PAGE_ADDED, {albumId,pageId,pageName})
    end

    Note over CTL,DB: COMMIT
    end

    SVC-->>CTL: PageListResponse
    CTL-->>FE: 200 ApiResponse[PageListResponse]
```

핵심 포인트
- `requireEditor` — VIEWER 는 페이지 생성 불가.
- 페이지 개수 30개 상한.
- WS 브로드캐스트(`page_event`) 와 SSE 알림(`NEW_PAGE_ADDED`) 이 별개 경로로 함께 발송된다.

---

## 3. 객체(Element) 추가 — WebSocket `push`

REST 가 아니라 `/sync/excalidraw/{albumId}` WebSocket 으로 들어온다.
도형 1개를 추가하면 변경된 elements 배열이 `push` 메시지로 올라오고, 서버는 LWW(Last-Write-Wins) merge → 메모리/Redis 즉시 갱신 → DB 는 5초 debounce 로 write-behind.

```mermaid
sequenceDiagram
    autonumber
    participant FE as Frontend<br/>(useExcalidrawSync)
    participant H as ExcalidrawSyncHandler
    participant ARP as AlbumRepository
    participant RC as MemberRoleCacheService<br/>(Redis)
    participant LOCK as pageLocks<br/>(pageId 단위 락)
    participant PDS as PageDocumentStore
    participant DIFF as ElementDiffApplier
    participant CM as ClockManager
    participant REDIS as Redis
    participant DB as PostgreSQL
    participant WS as 다른 세션들

    Note over FE,H: 사전 조건: connect 완료 + 인증된 세션

    FE->>H: { type:"push", pageId,<br/>clientClock, elements:[...] }

    Note over H: 1) 차단 가드
    H->>ARP: findById(albumId).isLocked()
    ARP-->>H: locked?
    alt locked
        H-->>FE: { type:"error", error:"album-locked" }
    end

    H->>RC: getRole(albumId, userId)
    RC-->>H: AlbumRole
    alt VIEWER or null
        H-->>FE: { type:"error", error:"read-only" }
    end

    Note over H: 2) Rate limit (120/min/session)
    H->>H: pushCounters.increment()
    alt count 가 120 초과
        H-->>FE: { type:"error", error:"rate-limit-exceeded" }
    end

    Note over H: 3) payload 크기 검증 (10MB)
    alt size 가 10MB 초과
        H-->>FE: { type:"error", error:"payload-too-large" }
    end

    Note over H,REDIS: 4) LWW merge — pageId 단위 락
    H->>LOCK: synchronized(pageLocks[pageId])
    activate LOCK
    LOCK->>PDS: loadElements(pageId)
    PDS->>PDS: memory hit?
    alt miss
        PDS->>REDIS: GET excalidraw:page:{pageId}
        alt miss
            PDS->>DB: SELECT excalidraw_pages
            DB-->>PDS: elements, serverClock
            PDS->>CM: initialize(pageId, serverClock)
        end
        PDS->>REDIS: SET (warm cache)
    end
    PDS-->>LOCK: server elements JSON

    LOCK->>DIFF: merge(server, incoming)<br/>version + versionNonce 비교
    DIFF-->>LOCK: MergeResult{ elements, rebased }

    LOCK->>DIFF: countNonDeleted(merged)
    alt count 가 500 초과
        LOCK-->>H: 종료
        H-->>FE: { type:"error", error:"shape-limit-exceeded" }
    end

    LOCK->>CM: increment(pageId) → newClock
    LOCK->>PDS: applyAndStore(pageId, merged, newClock)
    PDS->>PDS: pageStates.put (memory)
    PDS->>REDIS: SET excalidraw:page:{pageId}
    PDS->>PDS: resetFlushTimer(5초 debounce)
    deactivate LOCK

    Note over H,FE: 5) sender 에게 ack
    H-->>FE: { type:"push_result",<br/>clientClock, serverClock=newClock,<br/>action: commit|rebase }

    Note over H,WS: 6) 변경된 element 만 diff broadcast
    H->>DIFF: getDiffElements(server, merged)
    DIFF-->>H: diffNodes, deletedIds
    loop 같은 방의 다른 세션
        H->>WS: { type:"patch", serverClock,<br/>pageId, elements:diff, deletedIds }
    end

    Note over PDS,DB: 7) 5초 뒤 (또는 마지막 세션 퇴장 시 즉시)
    PDS->>DB: UPDATE excalidraw_pages<br/>SET elements=?, server_clock=?
```

핵심 포인트
- **LWW merge**: 각 element 의 `version` + `versionNonce` 로 server 측이 더 신선하면 클라이언트 변경을 버린다 (`rebase`). 그래서 sender 에게 `action: commit|rebase` 가 같이 회신됨.
- **pageId 단위 락**: `load → merge → store` 의 원자성 보장. 동시 push 가 들어와도 같은 페이지면 직렬화된다.
- **Write-Behind**:
  - memory(`ConcurrentHashMap`) + Redis 는 매 push 마다 즉시 갱신
  - DB 는 마지막 push 후 5초 idle 시 한 번만 UPDATE → 폭주하는 push 에 대해 DB write 폭증 방지
  - 방 마지막 세션 퇴장 시 / 앱 종료(`@PreDestroy`) 시 즉시 flush.
- **broadcast 는 diff 만**: 500개 도형 중 1개 변경 → 1개만 전송. 클라이언트는 `reconcileElements` 로 병합.

---

## 흐름 요약 (한 줄)

```
에디터 진입: REST GET album + GET pages (병렬) → WS open → connect(token,clocks) → connected(full|delta) + user_joined broadcast → files 바이너리는 GET /files/**
앨범 생성  : REST → @Transactional + BEFORE_COMMIT 리스너로 (Album + ADMIN + InviteLink + 기본 Page) 원자 생성
페이지 추가: REST → 권한/상한 검증 → DB INSERT → 같은 방에 page_event 브로드캐스트 + 멤버에게 SSE 알림
객체 추가  : WS push → 가드(잠금/권한/rate/size) → pageId 락 안에서 LWW merge → memory+Redis 즉시 / DB 5s debounce → push_result + diff patch
```
