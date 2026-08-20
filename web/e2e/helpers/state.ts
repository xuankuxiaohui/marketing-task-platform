import { readFileSync } from "node:fs";
import type { E2EState } from "../global-setup";
import { statePath } from "./env";

export function readE2EState(): E2EState {
  return JSON.parse(readFileSync(statePath(), "utf8")) as E2EState;
}
