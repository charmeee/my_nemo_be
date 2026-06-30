# Spring / JPA 어노테이션 메모

작업하면서 마주친 어노테이션·패턴 정리. 이 레포 코드 기준 실제 사용 예시 포함.

---

## 1. `@PrePersist` — JPA 라이프사이클 콜백

엔티티가 **DB에 INSERT 되기 직전**에 호출되는 콜백.
`EntityManager.persist()` 시점에 실행.

### 자주 쓰는 용도
- 생성 시각(`createdAt`) 자동 세팅
- 기본값 채우기
- UUID 등 식별자 생성
- 저장 전 유효성 검증

### 이 레포 사용 예 — `Album.java:37-41`

```java
@PrePersist
private void prePersist() {
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
}

@PreUpdate
private void preUpdate() {
    this.updatedAt = LocalDateTime.now();
}
```

### 라이프사이클 콜백 전체

| 어노테이션      | 호출 시점          |
| ---------- | -------------- |
| `@PrePersist`  | INSERT 직전      |
| `@PostPersist` | INSERT 직후      |
| `@PreUpdate`   | UPDATE 직전      |
| `@PostUpdate`  | UPDATE 직후      |
| `@PreRemove`   | DELETE 직전      |
| `@PostLoad`    | SELECT로 조회된 직후 |

### 참고
Spring Data JPA 사용 시, 직접 콜백을 짜는 대신
`@EntityListeners(AuditingEntityListener.class)` + `@CreatedDate` / `@LastModifiedDate`
조합으로 감사(auditing)를 처리하는 것이 더 일반적.

---

## 2. `Album.isLocked` — 동시성 락이 아니라 **권한 잠금**

이름이 `lock`이라 헷갈리지만 DB 락(비관적/낙관적)과 **무관**.
앨범을 **freeze** 시켜 ADMIN만 수정 가능하게 만드는 **비즈니스 플래그**.

### 핵심 로직 — `ImageService.java:73`

```java
if (album.isLocked() && albumMember.getRole() != AlbumRole.ADMIN) {
    throw new NemoException(ErrorCode.ALBUM_LOCKED);
}
```

### 동작표

| 상태                 | ADMIN    | EDITOR    | VIEWER     |
| ------------------ | -------- | --------- | ---------- |
| `isLocked = false` | 업로드 가능 | 업로드 가능 | (원래 막힘) |
| `isLocked = true`  | 업로드 가능 | **잠김**   | 잠김       |

### 시나리오
- 사진전 끝난 앨범 마감
- 결혼식 앨범 정리 끝
- "이제 더 추가하지 마" 상태 고정

### 동시성 제어와의 차이

| 구분          | DB 락                                | `isLocked`         |
| ----------- | ----------------------------------- | ------------------ |
| 목적          | 트랜잭션 충돌 방지                  | 비즈니스 권한 상태   |
| 구현          | `@Lock(PESSIMISTIC_WRITE)`, `@Version` | 단순 boolean 컬럼 |
| 지속 시간     | 트랜잭션 동안                       | 사용자가 unlock 할 때까지 |

> 더 명확한 네이밍은 `isFrozen` 또는 `isReadOnly`.

---

## 3. 클래스 레벨 `@Transactional(readOnly = true)`

Spring + JPA 진영의 **사실상 표준 관례**.
조회 메서드는 기본 readOnly, 쓰기 메서드만 명시적으로 override 한다.

### 패턴 — `AlbumMemberService` 등

```java
@Service
@Transactional(readOnly = true)   // ← 클래스 기본값
@RequiredArgsConstructor
public class AlbumMemberService {

    public AlbumMember findById(UUID id) { ... }   // readOnly 적용

    @Transactional                                  // ← 쓰기만 override
    public void invite(...) { ... }
}
```

### 이점

1. **안전한 기본값** — `@Transactional` 깜빡해도 조회는 트랜잭션 안에서 안전하게 실행
2. **성능** — Hibernate가 flush 모드를 MANUAL로 바꿔 더티 체킹 스냅샷 미생성
3. **read replica 라우팅** — 일부 DB 드라이버는 read-only 트랜잭션을 replica로 분산
4. **JDBC 힌트** — Connection 에 `setReadOnly(true)` 전달
5. **OSIV 환경에서도 일관된 트랜잭션 경계** 확보

### 함정

쓰기 메서드에 `@Transactional` 을 빼먹으면 **저장 안 되는 게 아니라 readOnly로 동작**한다.
특히 **더티 체킹 UPDATE 가 조용히 무시**되는 케이스 주의:

```java
// ❌ 클래스에 readOnly=true 인데 @Transactional 빼먹음
public void changeRole(UUID id, Role role) {
    AlbumMember m = repo.findById(id).orElseThrow();
    m.setRole(role);   // 더티 체킹 → flush 안 됨 → 변경 사라짐
}
```

→ 쓰기 메서드에는 **반드시 `@Transactional` override** 하기.

---

## 4. `@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")`

**IntelliJ 전용 인스펙션 억제 어노테이션.**
Java/Spring 문법과 무관하며 **런타임 동작에 영향 없음.**

### 무슨 경고를 끄나

IntelliJ가 빈 의존성 분석 시
`"Could not autowire. No beans of 'XXX' type found."`
같은 빨간줄을 띄우는 인스펙션을 꺼주는 것.

### 왜 false positive 가 나나

| 케이스                       | 이유                                   |
| ------------------------- | ------------------------------------ |
| MapStruct 매퍼              | 빌드 후에야 `XxxMapperImpl` 생성 → 정적 분석 못 봄 |
| QueryDSL Q 클래스           | 동일                                   |
| 다른 모듈/JAR 의 `@Configuration` | IntelliJ가 따라가지 못함                |
| `@ConditionalOn...` 조건부 빈   | 조건 분기 추적 불가                       |
| `@TestConfiguration` 빈      | 메인 컨텍스트 스캔 범위 밖                  |

### 사용 예

```java
@RequiredArgsConstructor
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class FooService {
    private final FooMapper mapper;   // MapStruct 생성 빈 → 경고 무시
}
```

### 주의

- 남용하면 **진짜 빈 누락 버그도 묻혀버림**
- 가능하면 **문제되는 필드 한 줄**에만 붙이는 게 안전
- 정말 false positive 가 확실할 때만 사용

### 비슷한 친구들

| ID                                     | 정의 주체     |
| -------------------------------------- | --------- |
| `SpringJavaInjectionPointsAutowiringInspection` | IntelliJ 인스펙션 |
| `SpringJavaAutowiringInspection`       | IntelliJ 인스펙션 |
| `unused`                                | IntelliJ 인스펙션 |
| `unchecked`                             | javac 표준  |
| `deprecation`                           | javac 표준  |
