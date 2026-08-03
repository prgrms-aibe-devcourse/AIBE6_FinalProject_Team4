const STORAGE_PREFIX = "gacha-open-batch:";
const STORAGE_VERSION = 1;
const MAX_BATCH_PACKS = 30;

interface StoredGachaBatch {
  version: number;
  createdAt: number;
  drawIds: number[];
}

function storageKey(batchKey: string) {
  return `${STORAGE_PREFIX}${batchKey}`;
}

function isValidDrawIds(value: unknown): value is number[] {
  return (
    Array.isArray(value) &&
    value.length >= 2 &&
    value.length <= MAX_BATCH_PACKS &&
    value.every((drawId) => Number.isInteger(drawId) && drawId > 0) &&
    new Set(value).size === value.length
  );
}

export function saveGachaBatch(drawIds: number[]) {
  if (!isValidDrawIds(drawIds)) {
    throw new Error("다중 팩 개봉 목록이 올바르지 않습니다.");
  }

  const batchKey = crypto.randomUUID();
  const payload: StoredGachaBatch = {
    version: STORAGE_VERSION,
    createdAt: Date.now(),
    drawIds,
  };
  sessionStorage.setItem(storageKey(batchKey), JSON.stringify(payload));
  return batchKey;
}

export function loadGachaBatch(batchKey: string): number[] | null {
  const raw = sessionStorage.getItem(storageKey(batchKey));
  if (!raw) return null;

  try {
    const payload = JSON.parse(raw) as Partial<StoredGachaBatch>;
    if (
      payload.version !== STORAGE_VERSION ||
      typeof payload.createdAt !== "number" ||
      !isValidDrawIds(payload.drawIds)
    ) {
      return null;
    }
    return payload.drawIds;
  } catch {
    return null;
  }
}

export function removeGachaBatch(batchKey: string) {
  sessionStorage.removeItem(storageKey(batchKey));
}
