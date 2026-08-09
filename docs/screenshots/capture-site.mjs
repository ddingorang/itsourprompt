// 배포본 화면 캡쳐. 실행: node capture-site.mjs <출력디렉토리> [샷이름...]
import { chromium } from 'playwright';
import fs from 'fs';
import path from 'path';

const BASE = 'https://lets.promptpractice.run';
const OUT = process.argv[2];
const ONLY = process.argv.slice(3);

// 제출까지 끝난 공개 어템프트. 문제 4(좌석 공유 오류) 1위 기록, 턴 3개.
const ATTEMPT = 267;

const tab = (page, name) => page.getByRole('tab', { name }).first().click();

const shots = [
  { name: '01-landing', path: '/' },
  { name: '02-problems', path: '/problems' },
  { name: '11-ranking', path: '/ranking?problem=4' },
  {
    name: '12-ranking-problems',
    path: '/ranking?problem=4',
    act: (page) => page.getByRole('button', { name: /전체\s*\d+/ }).first().click(),
  },
  { name: '03-workspace', path: '/problems/2', expand: true },
  { name: '04-workspace-game', path: '/problems/6', expand: true },
  { name: '05-workspace-python', path: '/problems/10', expand: true },
  // 턴이 있는 어템프트를 열면 앱이 「프롬프트 기록」 탭을 스스로 연다(기본값 아님).
  { name: '06-attempt-promptlog', path: `/attempts/${ATTEMPT}`, expand: true },
  {
    name: '07-attempt-problem',
    path: `/attempts/${ATTEMPT}`,
    expand: true,
    act: (page) => tab(page, '문제'),
  },
  {
    name: '08-attempt-grading',
    path: `/attempts/${ATTEMPT}`,
    expand: true,
    act: (page) => tab(page, '채점'),
  },
  // 피드백은 펼치지 않는다 — 코드 뷰어까지 풀면 4천 px가 넘어가 실제 화면과 딴판이 된다.
  { name: '09-feedback', path: `/attempts/${ATTEMPT}/feedback` },
  {
    name: '10-feedback-turn',
    path: `/attempts/${ATTEMPT}/feedback`,
    act: (page) => tab(page, '턴 2'),
  },
  { name: '13-login', path: '/login' },
  { name: '14-signup', path: '/signup' },
  { name: '15-error', path: '/error' },
  { name: '16-notfound', path: '/this-route-does-not-exist' },

  // 라이트 모드는 대표 화면만. 최근 작업이 대부분 라이트 모드 손질이라 결과를 남긴다.
  { name: 'light-01-landing', path: '/', mode: 'light' },
  { name: 'light-02-problems', path: '/problems', mode: 'light' },
  { name: 'light-03-attempt', path: `/attempts/${ATTEMPT}`, mode: 'light', expand: true },
  { name: 'light-04-feedback', path: `/attempts/${ATTEMPT}/feedback`, mode: 'light' },
  { name: 'light-05-ranking', path: '/ranking?problem=4', mode: 'light' },
];

const targets = ONLY.length ? shots.filter((s) => ONLY.includes(s.name)) : shots;

fs.mkdirSync(OUT, { recursive: true });
const browser = await chromium.launch();
const report = [];

for (const shot of targets) {
  const mode = shot.mode ?? 'dark';
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 2,
    locale: 'ko-KR',
    storageState: shot.auth && fs.existsSync(shot.auth) ? shot.auth : undefined,
  });
  // 다크/라이트는 prefers-color-scheme이 아니라 앱 자체 키다.
  await context.addInitScript((m) => {
    window.localStorage.setItem('prompt-practice-color-mode', m);
  }, mode);

  const page = await context.newPage();
  const problems = [];
  page.on('pageerror', (e) => problems.push(String(e).split('\n')[0]));
  page.on('console', (m) => m.type() === 'error' && problems.push(m.text().slice(0, 160)));

  try {
    await page.goto(BASE + shot.path, { waitUntil: 'networkidle', timeout: 60000 });
  } catch (e) {
    problems.push('goto: ' + e.message.split('\n')[0]);
  }
  await page.waitForTimeout(2500);

  let selected = '';
  if (shot.act) {
    try {
      await shot.act(page);
      await page.waitForTimeout(1500);
    } catch (e) {
      problems.push('act: ' + e.message.split('\n')[0]);
    }
    selected = await page.evaluate(() =>
      [...document.querySelectorAll('[role=tab][aria-selected=true]')]
        .map((t) => t.innerText.replace(/\s+/g, ' ').trim())
        .join(','),
    );
  }

  // 스크롤 등장 애니메이션을 깨우고 맨 위로 돌아온다. 뷰포트를 문서 높이로 키우면
  // min-h-screen 섹션이 같이 늘어나므로 fullPage에 맡긴다.
  await page.evaluate(async () => {
    const step = 600;
    for (let y = 0; y < document.documentElement.scrollHeight; y += step) {
      window.scrollTo(0, y);
      await new Promise((r) => setTimeout(r, 110));
    }
    window.scrollTo(0, document.documentElement.scrollHeight);
    await new Promise((r) => setTimeout(r, 400));
    window.scrollTo(0, 0);
  });
  await page.waitForTimeout(1000);

  let remaining = 0;
  if (shot.expand) {
    // 높이 고정 3분할 레이아웃은 fullPage로도 내부 스크롤이 잘린다.
    // 세로축만 푼다 — 가로/flex를 건드리면 컬럼이 폭을 나눠 갖지 못한다.
    remaining = await page.evaluate(() => {
      const clipped = () =>
        [...document.querySelectorAll('*')].filter(
          (e) => e.scrollHeight > e.clientHeight + 40 && e.clientHeight > 80,
        );
      const targets = clipped();
      if (!targets.length) return 0;
      const relax = (el) => {
        el.style.setProperty('height', 'auto', 'important');
        el.style.setProperty('max-height', 'none', 'important');
        el.style.setProperty('overflow-y', 'visible', 'important');
        const parent = el.parentElement;
        if (parent && getComputedStyle(parent).flexDirection.startsWith('column')) {
          el.style.setProperty('flex', 'none', 'important');
        }
      };
      for (const t of targets) {
        for (let el = t; el && el !== document.documentElement; el = el.parentElement) relax(el);
      }
      return clipped().length;
    });
    await page.waitForTimeout(900);
  }

  const file = path.join(OUT, `${shot.name}.png`);
  await page.screenshot({ path: file, fullPage: true });

  const info = await page.evaluate(() => ({
    text: document.body.innerText,
    height: document.documentElement.scrollHeight,
    overflowX: document.documentElement.scrollWidth > document.documentElement.clientWidth,
  }));
  report.push({
    name: shot.name,
    route: shot.path,
    landed: page.url().replace(BASE, ''),
    mode,
    px: info.height,
    kb: Math.round(fs.statSync(file).size / 1024),
    textLen: info.text.length,
    overflowX: info.overflowX,
    clippedLeft: remaining,
    head: info.text.slice(0, 120).replace(/\s+/g, ' '),
    problems: problems.slice(0, 3),
    selected,
  });
  console.log(
    `✓ ${shot.name.padEnd(26)} ${page.url().replace(BASE, '').padEnd(34)} ${String(info.height).padStart(6)}px  ${info.text.length} chars${selected ? '  [' + selected + ']' : ''}${problems.filter(p=>!p.includes('401')).length ? '  ⚠ ' + problems.filter(p=>!p.includes('401'))[0] : ''}`,
  );
  await context.close();
}

const reportPath = path.join(OUT, '_report.json');
const prior = fs.existsSync(reportPath) ? JSON.parse(fs.readFileSync(reportPath, 'utf8')) : [];
const merged = [...prior.filter((r) => !report.some((n) => n.name === r.name)), ...report].sort(
  (a, b) => a.name.localeCompare(b.name),
);
fs.writeFileSync(reportPath, JSON.stringify(merged, null, 2));
await browser.close();
