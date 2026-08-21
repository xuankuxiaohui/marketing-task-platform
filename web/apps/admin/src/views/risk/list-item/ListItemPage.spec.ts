import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";

vi.mock("@/api/risk", () => ({
  pageListItems: vi.fn(),
  addListItem: vi.fn(),
  importListItems: vi.fn(),
  removeListItem: vi.fn(),
}));

import { addListItem, importListItems, pageListItems, removeListItem } from "@/api/risk";
import ListItemPage from "./index.vue";

const pageMock = vi.mocked(pageListItems);
const addMock = vi.mocked(addListItem);
const importMock = vi.mocked(importListItems);
const removeMock = vi.mocked(removeListItem);

async function mountPage() {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().permissions = Object.values(PERMS);
  const wrapper = mount(ListItemPage, { global: { plugins: [pinia], directives: { auth } } });
  await flushPromises();
  return wrapper;
}

describe("RiskListItemPage", () => {
  beforeEach(() => {
    pageMock.mockReset();
    addMock.mockReset();
    importMock.mockReset();
    removeMock.mockReset();
    pageMock.mockResolvedValue(
      ok({
        total: 1,
        records: [
          {
            id: 7,
            dimension: "USER",
            listType: "BLACK",
            listValue: "9",
            reason: "abuse",
            denyLogin: false,
          },
        ],
      }),
    );
    addMock.mockResolvedValue(ok({ id: 8 }));
    importMock.mockResolvedValue(ok({ imported: 2, invalid: 1 }));
    removeMock.mockResolvedValue(ok({ ok: true }));
  });

  it("loads list items and adds a black entry with reason", async () => {
    const wrapper = await mountPage();
    expect(pageMock).toHaveBeenCalled();
    expect(wrapper.get('[data-testid="list-table"]').text()).toContain("9");
    await wrapper.get('[data-testid="list-create"]').trigger("click");
    await wrapper.get('[data-testid="list-value"]').setValue("11");
    await wrapper.get('[data-testid="list-reason"]').setValue("script");
    await wrapper.get('[data-testid="form-dialog"] form').trigger("submit.prevent");
    await flushPromises();
    expect(addMock).toHaveBeenCalledWith(
      expect.objectContaining({
        dimension: "USER",
        listType: "BLACK",
        listValue: "11",
        reason: "script",
      }),
    );
  });

  it("imports blacklist only and shows the result report", async () => {
    const wrapper = await mountPage();
    await wrapper.get('[data-testid="list-import"]').trigger("click");
    await wrapper.get('[data-testid="import-content"]').setValue("1\n2");
    await wrapper.get('[data-testid="import-reason"]').setValue("batch");
    await wrapper.get('[data-testid="form-dialog"] form').trigger("submit.prevent");
    await flushPromises();
    expect(importMock).toHaveBeenCalledWith({
      dimension: "USER",
      listType: "BLACK",
      content: "1\n2",
      reason: "batch",
    });
    expect(wrapper.get('[data-testid="import-result"]').text()).toBe(
      zhCN.list.importResult.replace("{imported}", "2").replace("{invalid}", "1"),
    );
  });

  it("removes an entry with a required reason", async () => {
    const wrapper = await mountPage();
    await wrapper.get('[data-testid="list-remove"]').trigger("click");
    await wrapper.get('[data-testid="remove-reason"]').setValue("expired");
    await wrapper.get('[data-testid="form-dialog"] form').trigger("submit.prevent");
    await flushPromises();
    expect(removeMock).toHaveBeenCalledWith(7, { reason: "expired" });
  });
});
