#!/usr/bin/env bash
# Excalidraw 이미지 fileId↔url 매핑 hydration 검증.
# 사용법:  ./scripts/test-image-hydration.sh
# 사전조건: nemo-be 가 :8080 에서 실행 중, postgres/redis 가용.

set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ASSET_DIR="$SCRIPT_DIR/../src/test/asset"
NODE_SCRIPT="$SCRIPT_DIR/test-ws-hydration.mjs"

PASS=0; FAIL=0
pass() { echo "  PASS: $1"; PASS=$((PASS+1)); }
fail() { echo "  FAIL: $1"; FAIL=$((FAIL+1)); }
die()  { echo "ERROR: $1"; exit 1; }

command -v jq >/dev/null || die "jq 필요"
command -v shasum >/dev/null || die "shasum 필요"
command -v node >/dev/null || die "node 필요"
[ -d "$ASSET_DIR" ] || die "asset 디렉토리 없음: $ASSET_DIR"

IMG1=$(find "$ASSET_DIR" -maxdepth 1 -type f \( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' -o -iname '*.webp' \) | sort | head -1)
[ -n "$IMG1" ] || die "asset 디렉토리에 이미지 없음"
echo "[asset] $IMG1"

# ─── 사전조건: 포트 listen 여부만 확인 ─────────────────────
echo "[0] 백엔드 reachability"
curl -sS -o /dev/null -m 3 "$BASE/" >/dev/null 2>&1 || die "백엔드 응답 없음 ($BASE)"

# ─── 1. 회원가입 ───────────────────────────────────────────
STAMP=$(date +%s%N)
EMAIL="hydra-$STAMP@test.com"
PW='passW0rd!'
echo "[1] register email=$EMAIL"
REG=$(curl -sS -X POST "$BASE/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PW\",\"nickname\":\"H\"}")
TOKEN=$(echo "$REG" | jq -r '.data.accessToken')
[ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] || die "register 실패: $REG"

# ─── 2. 앨범 생성 ──────────────────────────────────────────
echo "[2] create album"
ALBUM=$(curl -sS -X POST "$BASE/albums" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"hydration-test"}')
ALBUM_ID=$(echo "$ALBUM" | jq -r '.data.id')
[ -n "$ALBUM_ID" ] && [ "$ALBUM_ID" != "null" ] || die "album 생성 실패: $ALBUM"
echo "  albumId=$ALBUM_ID"

# ─── 3. 페이지 조회 (앨범 생성 시 기본 페이지 1개 만들어진다고 가정) ─
echo "[3] fetch pages"
PAGES=$(curl -sS -H "Authorization: Bearer $TOKEN" "$BASE/albums/$ALBUM_ID/pages")
PAGE_ID=$(echo "$PAGES" | jq -r '.data[0].pageId')
if [ -z "$PAGE_ID" ] || [ "$PAGE_ID" = "null" ]; then
  echo "  기본 페이지 없음 → 새 페이지 생성"
  PAGE_RES=$(curl -sS -X POST "$BASE/albums/$ALBUM_ID/pages" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -d '{"name":"P1"}')
  PAGE_ID=$(echo "$PAGE_RES" | jq -r '.data.pageId')
fi
[ -n "$PAGE_ID" ] && [ "$PAGE_ID" != "null" ] || die "pageId 못 가져옴"
echo "  pageId=$PAGE_ID"

# ─── 4. SHA-1 fileId 계산 (Excalidraw 클라이언트와 동일한 방식) ─
FILE_ID=$(shasum -a 1 "$IMG1" | awk '{print $1}')
echo "[4] fileId=$FILE_ID"

# ─── 5. 이미지 업로드 (excalidrawFileId 같이 전달) ─────────
echo "[5] upload image"
UP=$(curl -sS -X POST "$BASE/albums/$ALBUM_ID/images" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$IMG1" \
  -F "excalidrawFileId=$FILE_ID")
URL=$(echo "$UP" | jq -r '.data.url')
[ -n "$URL" ] && [ "$URL" != "null" ] || die "업로드 실패: $UP"
echo "  url=$URL"

# ─── 6. DB 검증: excalidraw_file_id 컬럼에 저장됐는지 ────
echo "[6] DB: images.excalidraw_file_id 저장 확인"
ROW=$(PGPASSWORD=nemo psql -h localhost -U nemo -d nemo -tA \
  -c "SELECT excalidraw_file_id FROM images WHERE excalidraw_file_id='$FILE_ID' LIMIT 1;")
if [ "$ROW" = "$FILE_ID" ]; then
  pass "DB row excalidraw_file_id=$FILE_ID 저장됨"
else
  fail "DB에 fileId 안 보임 (got='$ROW')"
fi

# ─── 7. WS 단계: push 후 재접속 → connected.files 검증 ────
echo "[7] WS push element + reconnect verify"
WS_OUT=$(BASE="$BASE" TOKEN="$TOKEN" ALBUM_ID="$ALBUM_ID" PAGE_ID="$PAGE_ID" FILE_ID="$FILE_ID" \
  node "$NODE_SCRIPT")
WS_OK=$(echo "$WS_OUT" | jq -r '.ok')
WS_URL=$(echo "$WS_OUT" | jq -r ".files[\"$FILE_ID\"] // empty")
if [ "$WS_OK" = "true" ] && [ -n "$WS_URL" ]; then
  pass "WS reconnect connected.files['$FILE_ID']='$WS_URL'"
else
  fail "WS hydration 매핑 누락: $WS_OUT"
fi

# ─── 8. REST 단계: GET /pages/{pageId}/elements 응답 검증 ─
echo "[8] REST GET /pages/{id}/elements"
EL=$(curl -sS -H "Authorization: Bearer $TOKEN" "$BASE/albums/$ALBUM_ID/pages/$PAGE_ID/elements")
REST_URL=$(echo "$EL" | jq -r ".data.files[\"$FILE_ID\"] // empty")
if [ -n "$REST_URL" ]; then
  pass "REST data.files['$FILE_ID']='$REST_URL'"
else
  fail "REST 응답에 files 매핑 누락: $EL"
fi

# ─── 결과 ────────────────────────────────────────────────
echo
echo "결과: PASS=$PASS  FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
