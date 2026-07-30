import type { ButtonHTMLAttributes, ReactNode } from 'react';

interface PaginationProps {
  currentPage: number;
  pageGroupEnd: number;
  pageGroupStart: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

interface PageButtonProps
  extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
  children: ReactNode;
  isCurrent?: boolean;
}

const pageButtonClasses =
  'size-10 cursor-pointer border transition-colors focus-visible:outline-none ' +
  'disabled:cursor-not-allowed disabled:opacity-45';

// Pagination controls use a compact square shape that differs from general action buttons.
function PageButton({
  children,
  className = '',
  isCurrent = false,
  type = 'button',
  ...buttonProps
}: PageButtonProps) {
  const currentPageClasses = isCurrent
    ? 'border-[#d6ff50] bg-[#d6ff50] text-[16px] font-black text-[#090909] ' +
      '[-webkit-text-stroke:0.35px_#090909]'
    : 'border-[#343434] bg-transparent text-[#a3a3a3] ' +
      'hover:border-[#d6ff50] hover:text-[#d6ff50] ' +
      'focus-visible:border-[#d6ff50] focus-visible:text-[#d6ff50]';

  return (
    <button
      {...buttonProps}
      className={`${pageButtonClasses} ${currentPageClasses} ${className}`}
      type={type}
    >
      {children}
    </button>
  );
}

export default function Pagination({
  currentPage,
  pageGroupEnd,
  pageGroupStart,
  totalPages,
  onPageChange,
}: PaginationProps) {
  const visiblePages = Array.from(
    { length: pageGroupEnd - pageGroupStart + 1 },
    (_, index) => pageGroupStart + index,
  );

  return (
    <nav
      className="flex items-center justify-center gap-2 pt-[54px] pb-8 font-mono text-[14px]"
      aria-label="페이지 이동"
    >
      <PageButton
        disabled={currentPage === 1}
        aria-label="첫 페이지로 이동"
        onClick={() => onPageChange(1)}
      >
        {'<<'}
      </PageButton>

      <PageButton
        disabled={currentPage === 1}
        aria-label="이전 페이지로 이동"
        onClick={() => onPageChange(currentPage - 1)}
      >
        {'<'}
      </PageButton>

      {visiblePages.map((page) => (
        <PageButton
          key={page}
          isCurrent={page === currentPage}
          aria-current={page === currentPage ? 'page' : undefined}
          aria-label={`${page}페이지`}
          onClick={() => onPageChange(page)}
        >
          {page}
        </PageButton>
      ))}

      <PageButton
        disabled={currentPage === totalPages}
        aria-label="다음 페이지로 이동"
        onClick={() => onPageChange(currentPage + 1)}
      >
        {'>'}
      </PageButton>

      <PageButton
        disabled={currentPage === totalPages}
        aria-label="마지막 페이지로 이동"
        onClick={() => onPageChange(totalPages)}
      >
        {'>>'}
      </PageButton>
    </nav>
  );
}
