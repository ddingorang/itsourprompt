/**
 * docs/erd.md 안의 mermaid 블록을 PNG로 뽑는다. 저장소에는 mermaid만 두고 이미지는 두지
 * 않는다 — 원본이 하나여야 그림이 조용히 낡지 않는다. 슬라이드나 노션에 넣을 그림이
 * 필요할 때만 돌린다.
 *
 * 준비 (저장소 의존성이 아니라 일회성이다. 아무 빈 디렉토리에서 받으면 된다):
 *   mkdir -p /tmp/erd && cd /tmp/erd
 *   npm i mermaid playwright && npx playwright install chromium
 *
 * 실행 (의존성을 받은 그 디렉토리에서 돌린다 — node_modules를 현재 위치에서 찾는다):
 *   node <저장소>/docs/render-erd.mjs ./out
 *
 * 파일명은 바로 앞 제목에서 따온다. 제목이 없으면 순번을 쓴다.
 */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const outDir = process.argv[2]

if (!outDir) {
    console.error('사용법: node <저장소>/docs/render-erd.mjs <출력_디렉토리>')
    console.error('  mermaid·playwright를 받아둔 디렉토리에서 돌려야 한다. 파일 상단 주석 참고.')
    process.exit(1)
}

// 스크립트는 저장소 안에 있고 의존성은 현재 디렉토리에 있다. 둘을 따로 찾아야 한다.
const requireFromCwd = createRequire(join(process.cwd(), '_'))
const { chromium } = requireFromCwd('playwright')

const docPath = join(dirname(fileURLToPath(import.meta.url)), 'erd.md')
const md = readFileSync(docPath, 'utf8')
const mermaidJs = readFileSync(join(process.cwd(), 'node_modules/mermaid/dist/mermaid.min.js'), 'utf8')

mkdirSync(outDir, { recursive: true })

/** 블록마다 (코드, 바로 앞 제목)을 모은다. */
const blocks = []
let heading = null

for (const line of md.split('\n')) {
    const isHeading = line.startsWith('#')

    if (isHeading) {
        heading = line.replace(/^#+\s*/, '').trim()
    }

    if (line.startsWith('```mermaid')) {
        blocks.push({ heading, lines: [], open: true })
        continue
    }

    const current = blocks[blocks.length - 1]

    if (current?.open) {
        if (line.startsWith('```')) {
            current.open = false
        } else {
            current.lines.push(line)
        }
    }
}

const slug = (text, index) =>
    (text ?? '')
        .toLowerCase()
        .replace(/[^가-힣a-z0-9]+/g, '-')
        .replace(/(^-|-$)/g, '') || `erd-${index + 1}`

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1800, height: 1200 }, deviceScaleFactor: 2 })
const used = new Map()

for (const [index, block] of blocks.entries()) {
    const code = block.lines.join('\n')
    const base = slug(block.heading, index)
    const seen = (used.get(base) ?? 0) + 1

    used.set(base, seen)

    const name = seen === 1 ? base : `${base}-${seen}`
    const html = `<!doctype html><html><head><meta charset="utf-8">
<style>body{margin:0;padding:24px;background:#fff}</style>
<script>${mermaidJs}<\/script></head>
<body><div id="c" class="mermaid">${code.replace(/</g, '&lt;')}</div>
<script>mermaid.initialize({startOnLoad:true,theme:'default'});<\/script></body></html>`
    const htmlPath = join(outDir, `${name}.html`)

    writeFileSync(htmlPath, html)
    await page.goto('file://' + htmlPath)
    await page.waitForSelector('#c svg', { timeout: 20000 })
    await page.waitForTimeout(400)
    await page.locator('#c svg').screenshot({ path: join(outDir, `${name}.png`) })

    console.log(`${name}.png  (${block.heading ?? '제목 없음'})`)
}

await browser.close()
