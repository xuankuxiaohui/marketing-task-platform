# GitHub MCP Server

MCP server for GitHub Issues and Pull Requests management.

## Setup

```bash
cd mcp/github-server
npm install
npm run build
```

## Configuration

Add to your MCP client config (e.g., `claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "github": {
      "command": "node",
      "args": ["mcp/github-server/dist/index.js"],
      "env": {
        "GITHUB_PERSONAL_ACCESS_TOKEN": "<your-token>",
        "GITHUB_REPOSITORY": "owner/repo"
      }
    }
  }
}
```

## Tools

| Tool | Description |
|------|-------------|
| `create_issue` | Create a GitHub issue with title, body, labels, assignees |
| `list_issues` | List issues with state/label filters |
| `get_issue` | Get a specific issue by number |
| `update_issue` | Update issue title, body, state, or labels |
| `add_issue_comment` | Add a comment to an issue |
| `create_pr` | Create a pull request |
| `list_prs` | List pull requests |
| `get_pr` | Get a specific PR by number |

## Environment Variables

- `GITHUB_PERSONAL_ACCESS_TOKEN` (required) — GitHub personal access token
- `GITHUB_REPOSITORY` (optional) — default repo in `owner/repo` format
