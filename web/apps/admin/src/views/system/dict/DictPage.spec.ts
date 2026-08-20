import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";

vi.mock("@/api/system", () => ({
  pageDictTypes: vi.fn(),
  createDictType: vi.fn(),
  updateDictType: vi.fn(),
  deleteDictType: vi.fn(),
  listDictEntries: vi.fn(),
  createDictEntry: vi.fn(),
}));

import { listDictEntries, pageDictTypes } from "@/api/system";
import DictPage from "./index.vue";

const pageMock = vi.mocked(pageDictTypes);
const entriesMock = vi.mocked(listDictEntries);

describe("DictManagePage", () => {
  beforeEach(() => {
    pageMock.mockReset();
    entriesMock.mockReset();
    pageMock.mockResolvedValue(
      ok({
        total: 1,
        records: [{ id: 1, code: "province", name: "省份", status: "ENABLED" }],
      }),
    );
    entriesMock.mockResolvedValue(ok([{ label: "广东", value: "GD", sort: 1 }]));
  });

  it("loads enabled entries for a selected type", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().permissions = Object.values(PERMS);
    const wrapper = mount(DictPage, { global: { plugins: [pinia], directives: { auth } } });
    await flushPromises();
    await wrapper.get('[data-testid="dict-type-select"]').trigger("click");
    await flushPromises();
    expect(entriesMock).toHaveBeenCalledWith("province");
    expect(wrapper.get('[data-testid="dict-entry-table"]').text()).toContain("GD");
  });
});
