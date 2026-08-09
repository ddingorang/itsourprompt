// 로그인 상태에서만 보이는 화면 재캡쳐(제출 기록이 생긴 뒤).
// 실행: node capture-auth.mjs <출력디렉토리> <끝난방번호>
import { chromium } from 'playwright';
import path from 'path';

const BASE = 'https://lets.promptpractice.run';
const OUT = process.argv[2];
const ROOM = Number(process.argv[3]);
const A = { username: 'shotuser1', password: process.env.SITE_PW };

const browser = await chromium.launch({
  args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'],
});

async function ctxFor(mode) {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 2,
    locale: 'ko-KR',
    permissions: ['microphone'],
  });
  await context.addInitScript((m) => {
    window.localStorage.setItem('prompt-practice-color-mode', m);
  }, mode);
  const res = await context.request.post(`${BASE}/api/auth/login`, { data: A });
  if (!res.ok()) throw new Error(`login: ${res.status()}`);
  return context;
}

const shots = [
  { name: '17-my', path: '/my', mode: 'dark' },
  { name: '17b-ranking-mine', path: '/ranking?problem=1', mode: 'dark' },
  { name: 'L6-relay-finished', path: `/relay/rooms/${ROOM}`, mode: 'light' },
  { name: 'L7-my', path: '/my', mode: 'light' },
];

for (const shot of shots) {
  const context = await ctxFor(shot.mode);
  const page = await context.newPage();
  await page.goto(BASE + shot.path, { waitUntil: 'networkidle', timeout: 60000 });
  await page.waitForTimeout(3000);
  await page.screenshot({ path: path.join(OUT, `${shot.name}.png`), fullPage: true });
  const text = await page.evaluate(() => document.body.innerText);
  console.log(`✓ ${shot.name.padEnd(22)} ${shot.mode.padEnd(5)} ${text.length} chars | ${text.slice(0, 80).replace(/\s+/g, ' ')}`);
  await context.close();
}

await browser.close();
