import type { TaskStepCommand, TaskTransitionCommand } from "@/api/task";

export const CANVAS_NODE_TYPE = "step";

const NODE_GAP_X = 220;
const NODE_Y = 80;

export type CanvasStepData = {
  code: string;
  name: string;
  seq: number;
  type: string;
  progressTarget?: number;
  prizeId?: number;
};

export type CanvasStepNode = {
  id: string;
  type: string;
  position: { x: number; y: number };
  label: string;
  data: CanvasStepData;
};

export type CanvasTransitionEdge = {
  id: string;
  source: string;
  target: string;
  label: string;
  data: {
    conditionExpr?: string;
    priority: number;
  };
};

export function stepsToNodes(steps: TaskStepCommand[] | undefined): CanvasStepNode[] {
  return (steps ?? []).map((step, index) => {
    const seq = step.seq ?? index + 1;
    return {
      id: step.code,
      type: CANVAS_NODE_TYPE,
      position: { x: (seq - 1) * NODE_GAP_X, y: NODE_Y },
      label: `${seq}. ${step.name} (${step.type})`,
      data: {
        code: step.code,
        name: step.name,
        seq,
        type: step.type,
        progressTarget: step.progressTarget,
        prizeId: step.prizeId,
      },
    };
  });
}

export function transitionsToEdges(transitions: TaskTransitionCommand[] | undefined): CanvasTransitionEdge[] {
  return (transitions ?? []).map((edge, index) => {
    const priority = edge.priority ?? 0;
    const cond = edge.conditionExpr ? edge.conditionExpr : "无条件";
    return {
      id: `${edge.fromStepCode}->${edge.toStepCode}:${index}`,
      source: edge.fromStepCode,
      target: edge.toStepCode,
      label: `p${priority} ${cond}`,
      data: {
        conditionExpr: edge.conditionExpr,
        priority,
      },
    };
  });
}

export function nodesToSteps(nodes: CanvasStepNode[]): TaskStepCommand[] {
  return [...nodes]
    .sort((a, b) => a.data.seq - b.data.seq || a.data.code.localeCompare(b.data.code))
    .map((node) => ({
      code: node.data.code,
      name: node.data.name,
      seq: node.data.seq,
      type: node.data.type,
      progressTarget: node.data.progressTarget,
      prizeId: node.data.prizeId,
    }));
}

export function edgesToTransitions(edges: CanvasTransitionEdge[]): TaskTransitionCommand[] {
  return edges.map((edge) => ({
    fromStepCode: edge.source,
    toStepCode: edge.target,
    conditionExpr: edge.data.conditionExpr,
    priority: edge.data.priority,
  }));
}

export function nextSeq(nodes: CanvasStepNode[]): number {
  return nodes.reduce((max, node) => Math.max(max, node.data.seq), 0) + 1;
}
