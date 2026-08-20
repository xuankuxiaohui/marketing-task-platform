/** Appendix D client event codes. Do not invent. */
export const TRACK = {
  PAGE_VIEW: "page.view",
  PAGE_LEAVE: "page.leave",
  TASK_CARD_EXPOSURE: "task.card.exposure",
  TASK_DETAIL_VIEW: "task.detail.view",
  TASK_START_CLICK: "task.start.click",
  TASK_STEP_CLICK: "task.step.click",
  TASK_COMPLETE_VIEW: "task.complete.view",
  TASK_ABANDON_CLICK: "task.abandon.click",
  REWARD_CLAIM_CLICK: "reward.claim.click",
  REWARD_LIST_VIEW: "reward.list.view",
  POINTS_PAGE_VIEW: "points.page.view",
} as const;

export type TrackCode = (typeof TRACK)[keyof typeof TRACK];
