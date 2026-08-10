// 릴레이 한 판을 처음부터 끝까지 실제로 진행하며 단계마다 찍는다.
// 턴 제한시간을 최대(300초)로 잡는다 — 캡쳐하는 사이에 턴이 시간초과로 넘어가면
// 아무도 프롬프트를 못 낸 채 게임이 끝난다(첫 시도에서 실제로 그렇게 됐다).
// 실행: node relay-full.mjs <출력디렉토리>
import { chromium } from 'playwright';
import fs from 'fs';
import path from 'path';

const BASE = 'https://lets.promptpractice.run';
const OUT = process.argv[2];
const A = { username: 'shotuser1', password: process.env.SITE_PW };
const B = { username: 'shotuser2', password: process.env.SITE_PW };

fs.mkdirSync(OUT, { recursive: true });
const log = (...m) => console.log(...m);
const browser = await chromium.launch({
  args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'],
});

async function makeUser(account) {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 2,
    locale: 'ko-KR',
    permissions: ['microphone'],
  });
  await context.addInitScript(() => {
    window.localStorage.setItem('prompt-practice-color-mode', 'dark');
  });
  const res = await context.request.post(`${BASE}/api/auth/login`, { data: account });
  if (!res.ok()) throw new Error(`login ${account.username}: ${res.status()}`);
  return { context, page: await context.newPage(), api: context.request };
}

async function shot(user, name, { path: route, act, wait = 3000, reload } = {}) {
  if (route) await user.page.goto(BASE + route, { waitUntil: 'networkidle', timeout: 60000 });
  else if (reload) await user.page.reload({ waitUntil: 'networkidle' });
  await user.page.waitForTimeout(wait);
  if (act) await act(user.page);
  await user.page.waitForTimeout(1000);
  await user.page.screenshot({ path: path.join(OUT, `${name}.png`), fullPage: true });
  const text = await user.page.evaluate(() => document.body.innerText);
  log(`✓ ${name.padEnd(30)} ${text.length} chars | ${text.slice(0, 80).replace(/\s+/g, ' ')}`);
}

const a = await makeUser(A);
const b = await makeUser(B);
const room = await a.api
  .post(`${BASE}/api/relay/rooms`, {
    data: {
      maxParticipants: 3,
      name: '테스터1의 연습방',
      problemId: 1,
      totalLaps: 1,
      turnTimeLimitSeconds: 300,
    },
  })
  .then((r) => r.json());
const ROOM = room.roomId;
log('room:', ROOM);

// ── 로비 ─────────────────────────────────────────────────────
await shot(a, '19-relay-lobby', { path: '/relay' });
await shot(a, '20-relay-create', {
  path: '/relay',
  act: async (page) => {
    await page.locator('input[type=text]').first().fill('점심시간 한 판');
  },
});

// ── 대기실 ───────────────────────────────────────────────────
await b.api.post(`${BASE}/api/relay/rooms/${ROOM}/participants`);
await shot(a, '21-relay-waiting-host', { path: `/relay/rooms/${ROOM}` });
await shot(b, '22-relay-waiting-guest', { path: `/relay/rooms/${ROOM}` });

// ── 시작 → 진행 ──────────────────────────────────────────────
const started = await a.api.post(`${BASE}/api/relay/rooms/${ROOM}/start`);
log('start:', started.status());
await shot(a, '23-relay-my-turn', { reload: true, wait: 3500 });
await shot(b, '24-relay-other-turn', { reload: true, wait: 3500 });

// 1턴 — 응답을 기다리지 않고 대기자 화면(생성 중)을 찍는다.
const turn1 = a.api.post(`${BASE}/api/relay/rooms/${ROOM}/turns`, {
  data: { prompt: '표준 출력으로 Hello, World! 를 출력하도록 만들어줘.' },
  timeout: 240000,
});
await shot(b, '25-relay-generating', { wait: 8000 });
log('turn1:', (await turn1).status());

// 2턴 — 이제 테스터2 차례
await shot(b, '26-relay-my-turn-2', { reload: true, wait: 4000 });
const turn2 = await b.api.post(`${BASE}/api/relay/rooms/${ROOM}/turns`, {
  data: { prompt: '출력 문자열의 대소문자와 문장부호가 정확한지 확인하고 고쳐줘.' },
  timeout: 240000,
});
log('turn2:', turn2.status());

// ── 피드백 생성 → 종료 ───────────────────────────────────────
let status = '';
for (let i = 0; i < 40; i++) {
  status = (await a.api.get(`${BASE}/api/relay/rooms/${ROOM}`).then((r) => r.json())).status;
  log(`  ${i * 5}s status=${status}`);
  if (status === 'FINISHED') break;
  await a.page.waitForTimeout(5000);
}
await shot(a, '27-relay-finished', { reload: true, wait: 5000 });

const tabs = await a.page.getByRole('tab').all();
log('  finished tabs:', tabs.length);
if (tabs.length > 1) {
  await tabs[1].click();
  await shot(a, '28-relay-finished-turn', { wait: 2500 });
}

fs.writeFileSync(
  path.join(OUT, '_relay-final.json'),
  JSON.stringify(
    {
      room: await a.api.get(`${BASE}/api/relay/rooms/${ROOM}`).then((r) => r.json()),
      turns: await a.api.get(`${BASE}/api/relay/rooms/${ROOM}/turns`).then((r) => r.json()),
    },
    null,
    2,
  ),
);
await browser.close();
