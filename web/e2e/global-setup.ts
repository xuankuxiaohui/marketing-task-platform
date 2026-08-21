import { writeFileSync } from "node:fs";
import {
  E2E_PORTAL_PASSWORD,
  adminLogin,
  ensurePrize,
  ensurePublishedTask,
  registerPortalUser,
} from "./helpers/backend";
import { apiBase, statePath } from "./helpers/env";

export type E2EState = {
  apiBase: string;
  prizeId: number;
  taskId: number;
  loginUsername: string;
  loginPassword: string;
  portalToken: string;
  adminPassword: string;
};

export default async function globalSetup(): Promise<void> {
  const health = await fetch(`${apiBase()}/healthz`);
  if (!health.ok) {
    throw new Error(`staging compose not healthy at ${apiBase()}/healthz`);
  }
  const session = await adminLogin();
  const prizeId = await ensurePrize(session, "e2e_core_pts", "MANUAL");
  const taskId = await ensurePublishedTask(session, {
    code: "e2e_core",
    name: "e2e core journey",
    steps: [
      { code: "clk", name: "click", seq: 1, type: "CLICK" },
      { code: "rwd", name: "reward", seq: 2, type: "REWARD", prizeId },
    ],
    transitions: [{ fromStepCode: "clk", toStepCode: "rwd" }],
  });
  const loginUsername = "e2e_login1";
  const portal = await registerPortalUser(loginUsername, E2E_PORTAL_PASSWORD);
  const state: E2EState = {
    apiBase: apiBase(),
    prizeId,
    taskId,
    loginUsername,
    loginPassword: E2E_PORTAL_PASSWORD,
    portalToken: portal.token,
    adminPassword: session.password,
  };
  writeFileSync(statePath(), `${JSON.stringify(state, null, 2)}\n`);
}
