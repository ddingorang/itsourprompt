interface UsePaginationOptions {
  itemCount: number;
  itemsPerPage: number;
  pagesPerGroup: number;
  requestedPage: number;
}

export function usePagination({
  itemCount,
  itemsPerPage,
  pagesPerGroup,
  requestedPage,
}: UsePaginationOptions) {
  const totalPages = Math.max(1, Math.ceil(itemCount / itemsPerPage));
  const currentPage =
    Number.isInteger(requestedPage) && requestedPage > 0
      ? Math.min(requestedPage, totalPages)
      : 1;
  const pageStart = (currentPage - 1) * itemsPerPage;
  const pageGroupStart =
    Math.floor((currentPage - 1) / pagesPerGroup) * pagesPerGroup + 1;
  const pageGroupEnd = Math.min(
    pageGroupStart + pagesPerGroup - 1,
    totalPages,
  );

  return {
    currentPage,
    pageGroupEnd,
    pageGroupStart,
    pageStart,
    totalPages,
  };
}
