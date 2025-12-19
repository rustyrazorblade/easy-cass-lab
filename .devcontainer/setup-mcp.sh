#!/bin/bash
# Configure MCP servers for Claude Code

set -e

echo "Configuring MCP servers..."

# Configure Serena MCP server (code understanding and refactoring)
claude mcp add serena -- uvx --from git+https://github.com/oraios/serena serena start-mcp-server --context claude-code --project-from-cwd

# Configure Context7 MCP server (library documentation lookup)
claude mcp add context7 -- npx -y @upstash/context7-mcp

echo "MCP servers configured successfully."
echo "Run 'claude mcp list' to verify."
