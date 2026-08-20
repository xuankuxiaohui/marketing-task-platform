/** Fixed mask returned for masked configs (R8.2 / ConfigMasks.DISPLAY). */
export const CONFIG_MASK_DISPLAY = "******";

export type ConfigUpdateForm = {
  configGroup?: string;
  status?: string;
  valueType?: string;
  masked?: boolean;
  remark?: string;
  value?: string;
};

/**
 * PUT /admin/system/configs/{key}: omit `value` to keep the stored secret.
 * Never send null / empty string (backend rejects as common.param-invalid).
 */
export function buildConfigUpdateBody(
  original: { configValue?: string; masked?: boolean },
  form: ConfigUpdateForm,
): Record<string, unknown> {
  const body: Record<string, unknown> = {};
  if (form.configGroup !== undefined) {
    body.configGroup = form.configGroup;
  }
  if (form.status !== undefined) {
    body.status = form.status;
  }
  if (form.valueType !== undefined) {
    body.valueType = form.valueType;
  }
  if (form.masked !== undefined) {
    body.masked = form.masked;
  }
  if (form.remark !== undefined) {
    body.remark = form.remark;
  }
  const raw = form.value;
  const keepOriginal =
    raw === undefined ||
    raw === "" ||
    (Boolean(original.masked) && (raw === CONFIG_MASK_DISPLAY || raw === original.configValue));
  if (!keepOriginal) {
    body.value = raw;
  }
  return body;
}
