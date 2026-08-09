// mono 스택에는 한글 글리프가 없어 윈도우에서 굴림으로 떨어진다.
// 한글만 헤더 메뉴와 같은 Noto Sans KR로 그려지도록 뒤에 붙인다.
const footerFontClasses =
  "[font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,'Liberation_Mono','Courier_New',monospace,'Noto_Sans_KR',sans-serif]";

export default function Footer() {
  return (
    <footer
      className={`border-t border-[var(--theme-border,#343434)] px-[5vw] py-5 text-center text-[11px] tracking-[0.04em] text-[var(--theme-subtle,#777)] max-[640px]:px-5 ${footerFontClasses}`}
    >
      © 2026 모두의 프롬프트. All rights reserved.
    </footer>
  );
}
