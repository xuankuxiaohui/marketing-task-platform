import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import type { TaskCardView } from "@/api/task";
import { zhCN } from "@/locales/zh-CN";
import TaskCard from "./TaskCard.vue";

vi.mock("@/tracking", () => ({
  observeTaskCardExposure: vi.fn(() => () => undefined),
}));

function card(overrides: Partial<TaskCardView> = {}): TaskCardView {
  return {
    taskId: 7,
    taskCode: "t7",
    name: "每日浏览",
    category: "daily",
    iconUrl: "https://cdn.example/a.png",
    rewardPreview: { firstName: "积分礼包", totalCount: 1 },
    userStatus: "NOT_STARTED",
    sortWeight: 1,
    ...overrides,
  };
}

describe("TaskCard", () => {
  it("maps list userStatus to R34.2 button copy", () => {
    const claimable = mount(TaskCard, { props: { task: card() } });
    expect(claimable.get('[data-testid="task-card-action-7"]').text()).toBe(zhCN.task.claim);
    expect(
      (claimable.get('[data-testid="task-card-action-7"]').element as HTMLButtonElement).disabled,
    ).toBe(false);

    const running = mount(TaskCard, { props: { task: card({ userStatus: "IN_PROGRESS" }) } });
    expect(running.get('[data-testid="task-card-action-7"]').text()).toBe(zhCN.task.continue);

    const done = mount(TaskCard, { props: { task: card({ userStatus: "COMPLETED" }) } });
    expect(done.get('[data-testid="task-card-action-7"]').text()).toBe(zhCN.task.completed);
    expect((done.get('[data-testid="task-card-action-7"]').element as HTMLButtonElement).disabled).toBe(
      true,
    );
  });

  it("renders the reward preview formula and emits open / action", async () => {
    const wrapper = mount(TaskCard, { props: { task: card() } });
    expect(wrapper.get('[data-testid="task-card-reward"]').text()).toContain("积分礼包");
    await wrapper.get('[data-testid="task-card-open"]').trigger("click");
    await wrapper.get('[data-testid="task-card-action-7"]').trigger("click");
    expect(wrapper.emitted("open")).toHaveLength(1);
    expect(wrapper.emitted("action")).toHaveLength(1);
  });
});
