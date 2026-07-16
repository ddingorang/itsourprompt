# Frontend

Vite + React 기반 Hello World 화면.

## 기술 스택

- Node 24
- TypeScript 6.0.x
- React 19.2.7
- Vite 7

## 실행

```bash
npm install
npm run dev
```

개발 서버는 `http://localhost:5173`에서 실행되며, 화면에 **Hello World**가 표시됩니다.

## 빌드

```bash
npm run build      # tsc 타입체크 + vite 프로덕션 빌드 -> dist/
npm run preview    # 빌드 결과 미리보기
```

## 구조

```
frontend/
├── index.html
├── package.json
├── vite.config.ts
├── tsconfig*.json
└── src/
    ├── main.tsx      # React 진입점
    └── App.tsx       # Hello World 컴포넌트
```
