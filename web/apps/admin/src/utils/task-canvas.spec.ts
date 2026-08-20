import { describe, expect, it } from "vitest";
import {
  edgesToTransitions,
  nextSeq,
  nodesToSteps,
  stepsToNodes,
  transitionsToEdges,
} from "./task-canvas";

describe("task canvas mapping", () => {
  it("round-trips steps and transitions for vue-flow", () => {
    const steps = [
      { code: "go", name: "浏览", seq: 1, type: "CLICK" },
      { code: "reward", name: "发奖", seq: 2, type: "REWARD", prizeId: 9 },
    ];
    const transitions = [{ fromStepCode: "go", toStepCode: "reward", conditionExpr: undefined, priority: 0 }];
    const nodes = stepsToNodes(steps);
    const edges = transitionsToEdges(transitions);
    expect(nodes.map((node) => node.id)).toEqual(["go", "reward"]);
    expect(nodes[1]?.label).toContain("REWARD");
    expect(edges[0]?.source).toBe("go");
    expect(nodesToSteps(nodes)).toEqual(steps);
    expect(edgesToTransitions(edges)).toEqual([{ fromStepCode: "go", toStepCode: "reward", conditionExpr: undefined, priority: 0 }]);
    expect(nextSeq(nodes)).toBe(3);
  });
});
