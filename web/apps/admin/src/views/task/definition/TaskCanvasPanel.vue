<script setup lang="ts">
import { VueFlow } from "@vue-flow/core";
import type { CanvasStepNode, CanvasTransitionEdge } from "@/utils/task-canvas";
import "@vue-flow/core/dist/style.css";
import "@vue-flow/core/dist/theme-default.css";

defineOptions({ name: "TaskCanvasPanel" });

const nodes = defineModel<CanvasStepNode[]>("nodes", { required: true });
const edges = defineModel<CanvasTransitionEdge[]>("edges", { required: true });

const emit = defineEmits<{
  selectNode: [id: string];
  selectEdge: [id: string];
}>();

function onNodeClick(payload: { node?: { id: string } }): void {
  if (payload.node?.id) {
    emit("selectNode", payload.node.id);
  }
}

function onEdgeClick(payload: { edge?: { id: string } }): void {
  if (payload.edge?.id) {
    emit("selectEdge", payload.edge.id);
  }
}
</script>

<template>
  <div class="task-canvas" data-testid="task-canvas">
    <VueFlow
      v-model:nodes="nodes"
      v-model:edges="edges"
      :nodes-draggable="true"
      fit-view-on-init
      @node-click="onNodeClick"
      @edge-click="onEdgeClick"
    />
  </div>
</template>
