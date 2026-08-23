import { config, DOMWrapper } from "@vue/test-utils";
import Antd from "ant-design-vue";

config.global.plugins.push(Antd);

const nativeSetValue = DOMWrapper.prototype.setValue;

DOMWrapper.prototype.setValue = async function patchedSetValue(value: unknown) {
  const el = this.element as HTMLElement;
  if (el.tagName === "INPUT" || el.tagName === "TEXTAREA" || el.tagName === "SELECT") {
    return nativeSetValue.call(this, value);
  }
  const inst = (el as { __vueParentComponent?: { emit: (e: string, v: unknown) => void } }).__vueParentComponent;
  if (inst?.emit) {
    inst.emit("update:value", value);
    inst.emit("update:checked", value);
    inst.emit("update:modelValue", value);
    return;
  }
  const inner = el.querySelector("input, textarea, select");
  if (inner) {
    return nativeSetValue.call(new DOMWrapper(inner), value);
  }
  return nativeSetValue.call(this, value);
};
