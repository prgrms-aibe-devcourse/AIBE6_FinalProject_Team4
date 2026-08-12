export type SearchParamValue = string | string[] | null | undefined;

export function firstSearchParam(value: SearchParamValue) {
  return Array.isArray(value) ? value[0] : (value ?? undefined);
}

export function parseOneBasedPage(value: SearchParamValue) {
  const page = Number(firstSearchParam(value));
  return Number.isInteger(page) && page > 0 ? page - 1 : 0;
}
