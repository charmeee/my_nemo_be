// Node 22 native WebSocket 사용. 외부 의존성 없음.
// 두 단계: (1) WS connect → 'push'로 image element 1개 등록 → 닫기
//          (2) WS 재접속 → 'connected' 메시지의 files 매핑을 stdout에 JSON으로 출력
//
// 입력 env: BASE, TOKEN, ALBUM_ID, PAGE_ID, FILE_ID

const BASE = process.env.BASE ?? 'http://localhost:8080';
const TOKEN = process.env.TOKEN;
const ALBUM_ID = process.env.ALBUM_ID;
const PAGE_ID = process.env.PAGE_ID;
const FILE_ID = process.env.FILE_ID;

if (!TOKEN || !ALBUM_ID || !PAGE_ID || !FILE_ID) {
  console.error('Missing env: TOKEN/ALBUM_ID/PAGE_ID/FILE_ID');
  process.exit(2);
}

const WS_URL = BASE.replace(/^http/, 'ws') + `/sync/excalidraw/${ALBUM_ID}`;

function imageElement(fileId) {
  return {
    id: 'hydration-test-img-1',
    type: 'image',
    x: 100, y: 100, width: 200, height: 200,
    angle: 0,
    strokeColor: 'transparent', backgroundColor: 'transparent',
    fillStyle: 'solid', strokeWidth: 2, strokeStyle: 'solid',
    roughness: 1, opacity: 100,
    groupIds: [], frameId: null, index: 'a0',
    roundness: null, seed: 12345, version: 5, versionNonce: 67890,
    isDeleted: false, boundElements: [], updated: Date.now(),
    link: null, locked: false,
    status: 'saved', fileId, scale: [1, 1], crop: null,
  };
}

function connect() {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(WS_URL);
    const t = setTimeout(() => { try { ws.close(); } catch {} reject(new Error('ws open timeout')); }, 8000);
    ws.addEventListener('open', () => { clearTimeout(t); resolve(ws); });
    ws.addEventListener('error', (e) => { clearTimeout(t); reject(new Error('ws error')); });
  });
}

function waitFor(ws, predicate, timeoutMs = 8000) {
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('waitFor timeout')), timeoutMs);
    const handler = (ev) => {
      let msg; try { msg = JSON.parse(ev.data); } catch { return; }
      if (predicate(msg)) {
        clearTimeout(t);
        ws.removeEventListener('message', handler);
        resolve(msg);
      }
    };
    ws.addEventListener('message', handler);
  });
}

async function step1Push() {
  const ws = await connect();
  ws.send(JSON.stringify({
    type: 'connect', token: TOKEN, lastClockByPage: {}, currentPageId: PAGE_ID, clientId: crypto.randomUUID(),
  }));
  await waitFor(ws, (m) => m.type === 'connected');
  ws.send(JSON.stringify({
    type: 'push', clientClock: 1, pageId: PAGE_ID, elements: [imageElement(FILE_ID)],
  }));
  await waitFor(ws, (m) => m.type === 'push_result');
  ws.close();
}

async function step2VerifyHydration() {
  const ws = await connect();
  ws.send(JSON.stringify({
    type: 'connect', token: TOKEN, lastClockByPage: {}, currentPageId: PAGE_ID, clientId: crypto.randomUUID(),
  }));
  const connected = await waitFor(ws, (m) => m.type === 'connected');
  ws.close();
  return connected.files ?? {};
}

(async () => {
  try {
    await step1Push();
    // 잠시 대기 — Write-Behind 디바운스가 메모리에 반영되는 데 약간 시간이 걸릴 수 있음
    await new Promise((r) => setTimeout(r, 300));
    const files = await step2VerifyHydration();
    process.stdout.write(JSON.stringify({ ok: true, files }));
  } catch (e) {
    process.stdout.write(JSON.stringify({ ok: false, error: String(e?.message ?? e) }));
    process.exit(1);
  }
})();
