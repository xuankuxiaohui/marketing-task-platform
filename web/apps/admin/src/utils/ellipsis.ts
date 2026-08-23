export const ELLIPSIS_LIMIT = 36;

export function clipCellText(
  value: unknown,
  max = ELLIPSIS_LIMIT,
): { display: string; full: string; clipped: boolean } {
  const full = value == null ? "" : String(value);
  if (full.length <= max) {
    return { display: full, full, clipped: false };
  }
  return { display: `${full.slice(0, max)}…`, full, clipped: true };
}
