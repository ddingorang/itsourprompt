import { Link } from 'react-router-dom';
import Header from '../shared/components/Header';

const stats = [
  { label: 'SOLVED', value: '12' },
  { label: 'SUBMISSIONS', value: '28' },
  { label: 'STREAK', value: '04', unit: 'DAYS' },
];

const recentActivity = [
  {
    id: 1,
    date: '2026.07.27',
    title: 'Hello World 출력',
  },
  {
    id: 2,
    date: '2026.07.25',
    title: 'SSAFY 출력',
  },
  {
    id: 3,
    date: '2026.07.22',
    title: '환영 메시지 출력',
  },
];

export default function MyPage() {
  return (
    <div className="min-h-screen min-w-80 bg-[#090909] text-[#f5f5ef] [font-family:Arial,'Noto_Sans_KR',sans-serif]">
      <Header>
        <nav className="flex items-center gap-7">
          <Link
            className="text-[#a3a3a3] transition-colors hover:text-[#d6ff50] focus-visible:text-[#d6ff50] focus-visible:outline-none max-[480px]:hidden"
            to="/problems"
          >
            PROBLEMS
          </Link>
          <span className="text-[#d6ff50]">MY PAGE</span>
        </nav>
      </Header>

      <main className="mx-auto w-[calc(100%_-_10vw)] pt-[clamp(36px,5vw,64px)] pb-24 max-[640px]:w-[calc(100%_-_40px)]">
        <div className="mb-[54px] font-mono text-[clamp(36px,6vw,64px)] leading-[0.82] font-bold tracking-[-0.04em] text-[#d6ff50]">
          USER PROFILE
        </div>

        <section className="grid grid-cols-[minmax(280px,0.8fr)_minmax(0,1.2fr)] border-y border-[#f5f5ef] max-[820px]:grid-cols-1">
          <div className="flex min-h-[220px] flex-col justify-between border-r border-[#343434] p-[clamp(24px,4vw,48px)] max-[820px]:min-h-[200px] max-[820px]:border-r-0 max-[820px]:border-b">
            <div className="flex items-center gap-5">
              <div
                className="grid size-16 shrink-0 place-items-center rounded-full bg-[#d6ff50] text-2xl font-black text-[#090909]"
                aria-hidden="true"
              >
                P
              </div>
              <div>
                <p className="mb-1 font-mono text-[13px] tracking-[0.12em] text-[#777]">
                  USER NAME
                </p>
                <h1 className="text-[clamp(28px,4vw,42px)] leading-none font-black tracking-[-0.05em]">
                  프롬프터
                </h1>
              </div>
            </div>

            <div>
              <span className="inline-flex items-center gap-2 font-mono text-[13px] tracking-[0.08em] text-[#777]">
                <span className="size-1.5 rounded-full bg-[#d6ff50]" />
                MEMBER SINCE 2026.07
              </span>
            </div>
          </div>

          <div className="grid grid-cols-3 max-[520px]:grid-cols-1">
            {stats.map((stat, index) => (
              <div
                className="flex min-h-[150px] flex-col justify-between border-r border-[#343434] p-[clamp(18px,3vw,32px)] last:border-r-0 max-[520px]:min-h-[120px] max-[520px]:border-r-0 max-[520px]:border-b max-[520px]:last:border-b-0"
                key={stat.label}
              >
                <span className="font-mono text-[13px] tracking-[0.1em] text-[#777]">
                  0{index + 1} / {stat.label}
                </span>
                <div className="flex items-baseline gap-2">
                  <strong className="font-mono text-[clamp(32px,5vw,60px)] leading-none tracking-[-0.08em]">
                    {stat.value}
                  </strong>
                  {stat.unit && (
                    <span className="font-mono text-[12px] text-[#d6ff50]">
                      {stat.unit}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        </section>

        <section className="mt-[clamp(52px,8vw,96px)]">
          <div className="flex items-end justify-between gap-6 pb-5">
              <div className="font-mono text-[clamp(26px,4vw,48px)] leading-[0.82] font-bold tracking-[-0.04em] text-[#d6ff50]">
                YOUR PROGRESS
              </div>
          </div>

          <div className="border-y border-t-[#f5f5ef] border-b-[#343434]">
            {recentActivity.map((activity, index) => (
              <Link
                className="group grid min-h-[92px] grid-cols-[52px_110px_minmax(0,1fr)_42px] items-center gap-4 border-b border-[#343434] px-2 transition-[background,padding] last:border-b-0 hover:bg-[#171717] hover:px-4 focus-visible:bg-[#171717] focus-visible:px-4 focus-visible:outline-none max-[680px]:grid-cols-[38px_minmax(0,1fr)_28px] max-[680px]:gap-3"
                key={activity.id}
                to={`/problems/${activity.id}`}
              >
                <span className="font-mono text-[17px] text-[#777]">
                  {String(index + 1).padStart(2, '0')}
                </span>
                <span className="font-mono text-[13px] text-[#777] max-[680px]:hidden">
                  {activity.date}
                </span>
                <strong className="truncate text-[clamp(15px,2vw,20px)] tracking-[-0.02em]">
                  {activity.title}
                </strong>
                <span
                  className="justify-self-end text-2xl text-[#d6ff50] transition-transform duration-200 group-hover:translate-x-[3px] group-hover:-translate-y-[3px] group-focus-visible:translate-x-[3px] group-focus-visible:-translate-y-[3px]"
                  aria-hidden="true"
                >
                  ↗
                </span>
              </Link>
            ))}
          </div>
        </section>

        <p className="mt-5 font-mono text-[10px] leading-5 tracking-[0.04em] text-[#555]">
          * 로그인 및 실제 학습 데이터 연동 전 표시되는 임시 화면입니다.
        </p>
      </main>
    </div>
  );
}
