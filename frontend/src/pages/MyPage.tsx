import { Link } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import Footer from '../shared/components/Footer';
import Header from '../shared/components/Header';

// [임시 데이터] 통계 3종은 아직 백엔드 API가 없어 더미 값이다.
// 실데이터 연동에는 예: GET /api/me/stats { solved, submissions, streakDays } 같은
// 신규 API가 필요하다 (S15P11A505-backend/docs/auth-api.md §6 후속 과제 참고).
const stats = [
  { label: 'SOLVED', value: '12' },
  { label: 'SUBMISSIONS', value: '28' },
  { label: 'STREAK', value: '04', unit: 'DAYS' },
];

// [임시 데이터] 활동 내역도 더미다. 실데이터 연동에는 어템프트에 소유자(userId)를
// 붙인 뒤 GET /api/me/attempts 로 조회하는 후속 작업이 필요하다.
// 주의: 아래 Link가 activity.id를 problemId로 그대로 쓰고 있으므로,
// 실데이터 연결 시 problemId를 별도 필드로 분리해야 한다.
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

/** 가입 시각(ISO 문자열)을 "YYYY.MM" 형태로 바꾼다. (MEMBER SINCE 표기용) */
function formatMemberSince(createdAt: string): string {
  const date = new Date(createdAt);
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}`;
}

export default function MyPage() {
  // 이 페이지는 ProtectedRoute로 감싸져 있어 user가 항상 존재한다(비로그인은 /login으로 이동됨).
  const { user } = useAuth();
  if (!user) {
    return null;
  }

  return (
    <div className="flex min-h-screen min-w-80 flex-col bg-[#090909] text-[#f5f5ef] [font-family:Arial,'Noto_Sans_KR',sans-serif]">
      <Header />

      <main className="mx-auto w-[min(calc(90%_-_360px),1040px)] flex-1 pt-[clamp(28px,4vw,44px)] pb-24 max-[900px]:w-[calc(100%_-_64px)] max-[640px]:w-[calc(100%_-_32px)] max-[640px]:pt-8">
        <div className="mb-[54px] font-mono text-[clamp(36px,6vw,64px)] leading-[0.82] font-bold tracking-[-0.04em] text-[#d6ff50]">
          USER PROFILE
        </div>

        <section className="grid grid-cols-[minmax(280px,0.8fr)_minmax(0,1.2fr)] border-y border-[#f5f5ef] max-[1200px]:grid-cols-1">
          <div className="flex min-h-[220px] flex-col justify-between border-r border-[#343434] p-[clamp(24px,4vw,48px)] max-[1200px]:min-h-[200px] max-[1200px]:border-r-0 max-[1200px]:border-b">
            <div className="flex items-center gap-5">
              <div
                className="grid size-16 shrink-0 place-items-center rounded-full bg-[#d6ff50] text-2xl font-black text-[#090909]"
                aria-hidden="true"
              >
                {/* 아바타 이니셜: 로그인 사용자 닉네임의 첫 글자 */}
                {user.nickname.charAt(0).toUpperCase()}
              </div>
              <div>
                <p className="mb-1 font-mono text-[13px] tracking-[0.12em] text-[#777]">
                  USER NAME
                </p>
                <h1 className="text-[clamp(28px,4vw,42px)] leading-none font-black tracking-[-0.05em]">
                  {user.nickname}
                </h1>
              </div>
            </div>

            <div>
              <span className="inline-flex items-center gap-2 font-mono text-[13px] tracking-[0.08em] text-[#777]">
                <span className="size-1.5 rounded-full bg-[#d6ff50]" />
                MEMBER SINCE {formatMemberSince(user.createdAt)}
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
          * 통계·활동 데이터는 추후 연동 예정입니다. (사용자 정보는 실제 데이터)
        </p>
      </main>
      <Footer />
    </div>
  );
}
