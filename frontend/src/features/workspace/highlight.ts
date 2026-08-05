import type { PrismTheme } from 'prism-react-renderer';

// 확장자 → prism 문법. 번들에 실린 문법 전부 + 사이드로드한 java.
// 문법은 트리셰이킹되지 않으므로 항목을 줄여도 번들이 1바이트도 안 준다 — 전부 싣는다.
const LANGUAGE_BY_EXTENSION: Record<string, string> = {
  java: 'java', // main.tsx에서 prismjs로부터 사이드로드
  js: 'javascript', mjs: 'javascript', cjs: 'javascript', jsx: 'jsx',
  ts: 'typescript', mts: 'typescript', cts: 'typescript', tsx: 'tsx',
  css: 'css',
  // markup은 <style>/<script> 내부 CSS·JS까지 자동으로 칠한다
  html: 'markup', htm: 'markup', xhtml: 'markup', xml: 'markup',
  svg: 'markup', mathml: 'markup', rss: 'markup',
  json: 'json', webmanifest: 'webmanifest',
  yml: 'yaml', yaml: 'yaml',
  md: 'markdown', markdown: 'markdown',
  py: 'python', go: 'go', rs: 'rust', sql: 'sql', swift: 'swift',
  kt: 'kotlin', kts: 'kts',
  c: 'c', h: 'c',
  cpp: 'cpp', cc: 'cpp', cxx: 'cpp', hpp: 'cpp', hh: 'cpp', hxx: 'cpp',
  m: 'objectivec', mm: 'objectivec',
  graphql: 'graphql', gql: 'graphql',
  coffee: 'coffeescript', as: 'actionscript',
  re: 'reason', rei: 'reason', ssml: 'ssml', n4js: 'n4js', n4jsd: 'n4jsd',
  txt: 'plain', text: 'plain',
};

/** 파일 경로의 확장자로 강조 문법을 고른다. 모르면 plain(강조 없음). */
export function languageForPath(path?: string): string {
  const ext = path?.match(/\.([^./]+)$/)?.[1]?.toLowerCase();
  return (ext && LANGUAGE_BY_EXTENSION[ext]) || 'plain';
}

// VS Code 계열 팔레트. 색 값은 index.css의 --code-* 변수가 다크·라이트를 가른다.
// plain은 비워 컨테이너의 --code-viewer-text를 그대로 상속한다.
export const codeViewerTheme: PrismTheme = {
  plain: {},
  styles: [
    {
      types: ['keyword', 'boolean', 'selector', 'atrule', 'important', 'import'],
      style: { color: 'var(--code-keyword)' },
    },
    {
      types: ['class-name', 'builtin', 'generics', 'namespace'],
      style: { color: 'var(--code-type)' },
    },
    {
      types: ['string', 'char', 'attr-value', 'regex'],
      style: { color: 'var(--code-string)' },
    },
    {
      types: ['number', 'unit', 'hexcode'],
      style: { color: 'var(--code-number)' },
    },
    {
      types: ['comment', 'prolog', 'cdata', 'doctype', 'doctype-tag'],
      style: { color: 'var(--code-comment)', fontStyle: 'italic' },
    },
    {
      types: ['function', 'annotation', 'method'],
      style: { color: 'var(--code-function)' },
    },
    {
      types: ['operator', 'punctuation', 'entity'],
      style: { color: 'var(--code-punct)' },
    },
    { types: ['tag'], style: { color: 'var(--code-tag)' } },
    {
      types: ['attr-name', 'property', 'literal-property', 'constant', 'parameter', 'name'],
      style: { color: 'var(--code-attr)' },
    },
  ],
};
