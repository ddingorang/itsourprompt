import Button from '../shared/components/Button';
import Footer from '../shared/components/Footer';
import Header from '../shared/components/Header';

type AuthPlaceholderPageProps = {
  title: 'LOGIN' | 'SIGN UP';
};

export default function AuthPlaceholderPage({
  title,
}: AuthPlaceholderPageProps) {
  return (
    <div className="flex min-h-screen min-w-80 flex-col bg-[#090909] text-[#f5f5ef] [font-family:Arial,'Noto_Sans_KR',sans-serif]">
      <Header />

      <main className="mx-auto flex w-[calc(100%_-_10vw)] flex-1 items-center justify-center py-16 max-[640px]:w-[calc(100%_-_40px)]">
        <section className="w-full max-w-2xl border-y border-[#343434] py-12 text-center">
          <p className="font-mono text-xs tracking-[0.12em] text-[#d6ff50]">
            COMING SOON
          </p>
          <h1 className="mt-4 font-mono text-[clamp(44px,8vw,72px)] leading-none font-bold tracking-[-0.05em]">
            {title}
          </h1>
          <p className="mt-6 text-sm leading-7 text-[#a3a3a3]">
            {title === 'LOGIN'
              ? '로그인 기능을 준비 중입니다.'
              : '회원가입 기능을 준비 중입니다.'}
          </p>
          <Button className="mt-8" to="/">
            BACK TO HOME
          </Button>
        </section>
      </main>

      <Footer />
    </div>
  );
}
