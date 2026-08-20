import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import TaskTimeline from "./TaskTimeline.vue";

describe("TaskTimeline", () => {
  it("renders done / current / idle tones and progress x/N", () => {
    const wrapper = mount(TaskTimeline, {
      props: {
        currentStepCode: "visit",
        steps: [
          { stepCode: "guide", name: "引导", type: "PASSIVE", status: "COMPLETED" },
          { stepCode: "visit", name: "浏览", type: "CLICK", status: "ACTIVE" },
          { stepCode: "report", name: "上报", type: "PROGRESS", status: "INACTIVE", progressCurrent: 2, progressTarget: 5 },
          { stepCode: "reward", name: "发奖", type: "REWARD", status: "INACTIVE" },
        ],
      },
    });
    expect(wrapper.get('[data-testid="timeline-step-guide"]').attributes("data-tone")).toBe("done");
    expect(wrapper.get('[data-testid="timeline-step-guide"]').text()).toContain("✓");
    expect(wrapper.get('[data-testid="timeline-step-visit"]').attributes("data-tone")).toBe("current");
    expect(wrapper.get('[data-testid="timeline-step-report"]').attributes("data-tone")).toBe("idle");
    expect(wrapper.get('[data-testid="timeline-progress-label"]').text()).toBe("2/5");
    expect(wrapper.find('[data-testid="timeline-progress-bar"]').exists()).toBe(true);
  });
});
