import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { ok } from "@/test-utils/result";

vi.mock("@/api/activity", () => ({
  fetchActivities: vi.fn(),
  fetchActivityDetail: vi.fn(),
  postParticipate: vi.fn(),
}));

vi.mock("vant", async () => {
  const actual = await vi.importActual<typeof import("vant")>("vant");
  return {
    ...actual,
    showSuccessToast: vi.fn(),
    showFailToast: vi.fn(),
  };
});

import { fetchActivities, fetchActivityDetail, postParticipate } from "@/api/activity";
import ActivityPage from "./index.vue";

const listMock = vi.mocked(fetchActivities);
const detailMock = vi.mocked(fetchActivityDetail);
const joinMock = vi.mocked(postParticipate);

async function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/activity", component: ActivityPage },
      { path: "/mine", component: { template: "<div />" } },
    ],
  });
  await router.push("/activity");
  await router.isReady();
  const wrapper = mount(ActivityPage, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("ActivityPage", () => {
  beforeEach(() => {
    listMock.mockReset();
    detailMock.mockReset();
    joinMock.mockReset();
  });

  it("renders empty when no published activity", async () => {
    listMock.mockResolvedValue(ok([]));
    const wrapper = await mountPage();
    expect(wrapper.get('[data-testid="activity-empty"]').text()).toContain(zhCN.activity.empty);
  });

  it("renders sanitized html and participates", async () => {
    listMock.mockResolvedValue(ok([{ id: 3, code: "summer", name: "夏季专题" }]));
    detailMock.mockResolvedValue(
      ok({
        id: 3,
        code: "summer",
        name: "夏季专题",
        richText: "<p>hello</p>",
        contentHash: "abc",
        version: 1,
        submodules: [{ type: "TASK", refId: 8, sort: 0 }],
      }),
    );
    joinMock.mockResolvedValue(ok({ participationId: 9, result: "PASS", granted: true }));
    const wrapper = await mountPage();
    expect(wrapper.get('[data-testid="activity-name"]').text()).toContain("夏季专题");
    expect(wrapper.get('[data-testid="activity-html"]').html()).toContain("<p>hello</p>");
    await wrapper.get('[data-testid="activity-join"]').trigger("click");
    await flushPromises();
    expect(joinMock).toHaveBeenCalledWith(3);
    expect(wrapper.get('[data-testid="activity-result"]').text()).toContain("PASS");
  });
});
