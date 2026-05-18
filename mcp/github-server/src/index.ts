import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

const GITHUB_API = "https://api.github.com";
const TOKEN = process.env.GITHUB_PERSONAL_ACCESS_TOKEN;
const REPO = process.env.GITHUB_REPOSITORY || "";

if (!TOKEN) {
  console.error("GITHUB_PERSONAL_ACCESS_TOKEN is required");
  process.exit(1);
}

const headers = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: "application/vnd.github+json",
  "X-GitHub-Api-Version": "2022-11-28",
  "User-Agent": "github-mcp-server/1.0",
};

async function github(path: string, method = "GET", body?: unknown) {
  const url = path.startsWith("http") ? path : `${GITHUB_API}${path}`;
  const res = await fetch(url, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(`GitHub API error ${res.status}: ${err}`);
  }
  return res.status === 204 ? null : res.json();
}

function parseRepo(repo: string): [string, string] {
  const [owner, name] = repo.split("/");
  return [owner, name];
}

const server = new McpServer({
  name: "github-mcp-server",
  version: "1.0.0",
});

server.tool(
  "create_issue",
  "Create a GitHub issue",
  {
    repo: z.string().describe("Repository in owner/name format").optional(),
    title: z.string().describe("Issue title"),
    body: z.string().describe("Issue body (markdown)").optional(),
    labels: z.array(z.string()).describe("Labels to apply").optional(),
    assignees: z.array(z.string()).describe("GitHub usernames to assign").optional(),
  },
  async ({ repo, title, body, labels, assignees }) => {
    const [owner, name] = parseRepo(repo || REPO);
    const result = await github(`/repos/${owner}/${name}/issues`, "POST", {
      title,
      body,
      labels,
      assignees,
    });
    return {
      content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
    };
  }
);

server.tool(
  "create_pr",
  "Create a GitHub pull request",
  {
    repo: z.string().describe("Repository in owner/name format").optional(),
    title: z.string().describe("PR title"),
    body: z.string().describe("PR description (markdown)").optional(),
    head: z.string().describe("Source branch name"),
    base: z.string().describe("Target branch name").default("main"),
    draft: z.boolean().describe("Create as draft PR").optional(),
  },
  async ({ repo, title, body, head, base, draft }) => {
    const [owner, name] = parseRepo(repo || REPO);
    const result = await github(`/repos/${owner}/${name}/pulls`, "POST", {
      title,
      body,
      head,
      base,
      draft,
    });
    return {
      content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
    };
  }
);

server.tool(
  "list_issues",
  "List GitHub issues",
  {
    repo: z.string().describe("Repository in owner/name format").optional(),
    state: z.enum(["open", "closed", "all"]).default("open"),
    labels: z.string().describe("Comma-separated label filter").optional(),
    per_page: z.number().default(10),
  },
  async ({ repo, state, labels, per_page }) => {
    const [owner, name] = parseRepo(repo || REPO);
    const params = new URLSearchParams({ state, per_page: String(per_page) });
    if (labels) params.set("labels", labels);
    const result = await github(
      `/repos/${owner}/${name}/issues?${params}`
    );
    return {
      content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
    };
  }
);

server.tool(
  "get_issue",
  "Get a specific GitHub issue",
  {
    repo: z.string().describe("Repository in owner/name format").optional(),
    issue_number: z.number().describe("Issue number"),
  },
  async ({ repo, issue_number }) => {
    const [owner, name] = parseRepo(repo || REPO);
    const result = await github(
      `/repos/${owner}/${name}/issues/${issue_number}`
    );
    return {
      content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
    };
  }
);

server.tool(
  "update_issue",
  "Update a GitHub issue",
  {
    repo: z.string().describe("Repository in owner/name format").optional(),
    issue_number: z.number().describe("Issue number"),
    title: z.string().describe("New title").optional(),
    body: z.string().describe("New body").optional(),
    state: z.enum(["open", "closed"]).optional(),
    labels: z.array(z.string()).optional(),
  },
  async ({ repo, issue_number, title, body, state, labels }) => {
    const [owner, name] = parseRepo(repo || REPO);
    const result = await github(
      `/repos/${owner}/${name}/issues/${issue_number}`,
      "PATCH",
      { title, body, state, labels }
    );
    return {
      content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
    };
  }
);

server.tool(
  "add_issue_comment",
  "Add a comment to a GitHub issue",
  {
    repo: z.string().describe("Repository in owner/name format").optional(),
    issue_number: z.number().describe("Issue number"),
    body: z.string().describe("Comment body (markdown)"),
  },
  async ({ repo, issue_number, body }) => {
    const [owner, name] = parseRepo(repo || REPO);
    const result = await github(
      `/repos/${owner}/${name}/issues/${issue_number}/comments`,
      "POST",
      { body }
    );
    return {
      content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
    };
  }
);

server.tool(
  "get_pr",
  "Get a specific pull request",
  {
    repo: z.string().describe("Repository in owner/name format").optional(),
    pr_number: z.number().describe("PR number"),
  },
  async ({ repo, pr_number }) => {
    const [owner, name] = parseRepo(repo || REPO);
    const result = await github(`/repos/${owner}/${name}/pulls/${pr_number}`);
    return {
      content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
    };
  }
);

server.tool(
  "list_prs",
  "List pull requests",
  {
    repo: z.string().describe("Repository in owner/name format").optional(),
    state: z.enum(["open", "closed", "all"]).default("open"),
    per_page: z.number().default(10),
  },
  async ({ repo, state, per_page }) => {
    const [owner, name] = parseRepo(repo || REPO);
    const params = new URLSearchParams({ state, per_page: String(per_page) });
    const result = await github(`/repos/${owner}/${name}/pulls?${params}`);
    return {
      content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
    };
  }
);

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
}

main().catch((err) => {
  console.error("Failed to start MCP server:", err);
  process.exit(1);
});
