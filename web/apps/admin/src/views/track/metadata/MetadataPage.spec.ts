import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";

vi.mock("@/api/track", () => ({
  pageMetadata: vi.fn(),
  createMetadata: vi.fn(),
  updateMetadata: vi.fn(),
  deleteMetadata: vi.fn(),
}));

import { pageMetadata, updateMetadata } from "@/api/track";
import MetadataPage from "./index.vue";

const pageMock = vi.mocked(pageMetadata);
const updateMock = vi.mocked(updateMetadata);

async function mountPage() {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().permissions = Object.values(PERMS);
  const wrapper = mount(MetadataPage, { global: { plugins: [pinia], directives: { auth } } });
  await flushPromises();
  return wrapper;
}

describe("TrackMetadataPage", () => {
  beforeEach(() => {
    pageMock.mockReset();
    updateMock.mockReset();
    pageMock.mockResolvedValue(
      ok({
        total: 1,
        records: [
          {
            id: 3,
            eventCode: "page.view",
            name: "页面浏览",
            status: "ENABLED",
            owner: "ops",
            propSchema: [{ name: "route", type: "STRING", required: true }],
          },
        ],
      }),
    );
    updateMock.mockResolvedValue(ok({ ok: true }));
  });

  it("locks eventCode when updating metadata", async () => {
    const wrapper = await mountPage();
    expect(wrapper.get('[data-testid="metadata-table"]').text()).toContain("page.view");
    await wrapper.get('[data-testid="metadata-edit"]').trigger("click");
    expect((wrapper.get('[data-testid="metadata-code"]').element as HTMLInputElement).disabled).toBe(true);
    await wrapper.get('[data-testid="metadata-name"]').setValue("页面浏览-改");
    await wrapper.get('[data-testid="form-dialog"] form').trigger("submit.prevent");
    await flushPromises();
    expect(updateMock).toHaveBeenCalledWith(
      3,
      expect.objectContaining({
        eventCode: "page.view",
        name: "页面浏览-改",
        status: "ENABLED",
      }),
    );
  });
});
