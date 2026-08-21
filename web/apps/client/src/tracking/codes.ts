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
  SIGNIN_PAGE_VIEW: "signin.page.view",
  SIGNIN_SIGN_CLICK: "signin.sign.click",
  SIGNIN_CATCHUP_CLICK: "signin.catchup.click",
  AD_CAROUSEL_EXPOSURE: "ad.carousel.exposure",
  AD_SPLASH_EXPOSURE: "ad.splash.exposure",
  AD_POPUP_EXPOSURE: "ad.popup.exposure",
  AD_FLOAT_EXPOSURE: "ad.float.exposure",
  AD_IMAGE_EXPOSURE: "ad.image.exposure",
  AD_CAROUSEL_CLICK: "ad.carousel.click",
  AD_SPLASH_CLICK: "ad.splash.click",
  AD_POPUP_CLICK: "ad.popup.click",
  AD_FLOAT_CLICK: "ad.float.click",
  AD_IMAGE_CLICK: "ad.image.click",
} as const;

export type TrackCode = (typeof TRACK)[keyof typeof TRACK];
