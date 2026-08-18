"use client";

import { ApiError, SpringPage } from "@/lib/api";
import { useCallback, useEffect, useRef, useState } from "react";

// AdminInquiryPanel/AdminReportPanel 둘 다 "상태 필터 + 페이지네이션" 목록을 조회하는데,
// fetchPage는 signal 없이(제출 성공 후 reload() 호출 시)도, signal과 함께(필터/페이지
// 변경 시 useEffect 자동 취소)도 불린다 — 그 사이 사용자가 필터를 바꾸면 먼저 보낸 요청이
// 나중에 응답할 수 있어, requestId로 "가장 최근에 보낸 요청의 응답인지"를 확인해 뒤처진
// 응답이 최신 state를 덮어쓰지 않도록 막는다.
export function useAdminPaginatedList<T>(
  fetchPage: (page: number, signal?: AbortSignal) => Promise<SpringPage<T>>,
  fallbackErrorMessage: string,
) {
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<T[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const requestIdRef = useRef(0);

  const load = useCallback(
    (signal?: AbortSignal) => {
      const requestId = ++requestIdRef.current;
      setLoading(true);
      setErrorMessage("");

      return fetchPage(page, signal)
        .then((result) => {
          if (requestIdRef.current !== requestId) return;
          // 처리/답변 등으로 필터 조건을 벗어난 항목이 빠지면서 마지막 페이지가 사라질 수
          // 있다 — 그 경우 빈 결과를 그대로 보여주는 대신 이전 페이지로 물러나 다시 불러온다.
          // totalElements가 0(마지막 남은 한 건까지 처리돼 조건에 맞는 항목이 아예 없어진
          // 경우)이어도 롤백해야 한다 — 그래야 page가 0으로 돌아와 "0 / 0"인데 "이전" 버튼은
          // 활성인 상태로 멈추지 않는다. 롤백된 page=0 조회도 비어 있다면 그때는 page>0이
          // 아니므로 더 이상 롤백하지 않고 정상적인 빈 상태로 끝난다.
          if (result.content.length === 0 && page > 0) {
            setPage((current) => Math.max(0, current - 1));
            return;
          }
          setItems(result.content);
          setTotalPages(result.totalPages);
          setTotalElements(result.totalElements);
        })
        .catch((requestError) => {
          if (requestError instanceof DOMException && requestError.name === "AbortError") return;
          if (requestIdRef.current !== requestId) return;
          setItems([]);
          setErrorMessage(
            requestError instanceof ApiError ? requestError.message : fallbackErrorMessage,
          );
        })
        .finally(() => {
          if (requestIdRef.current === requestId) setLoading(false);
        });
    },
    [fetchPage, page, fallbackErrorMessage],
  );

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);

  return {
    page,
    setPage,
    items,
    totalPages,
    totalElements,
    loading,
    errorMessage,
    reload: () => void load(),
  };
}
