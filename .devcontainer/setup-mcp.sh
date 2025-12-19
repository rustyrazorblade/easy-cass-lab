#!/bin/bash
# Configure MCP servers for Claude Code

set -e

echo "Configuring MCP servers..."

# Configure Serena MCP server (code understanding and refactoring)
if ! claude mcp get serena > /dev/null 2>&1; then
    claude mcp add serena -- uvx --from git+https://github.com/oraios/serena serena start-mcp-server --context claude-code --project-from-cwd
    echo "Added serena MCP server"
else
    echo "MCP server serena already configured, skipping"
fi

# Configure Context7 MCP server (library documentation lookup)
if ! claude mcp get context7 > /dev/null 2>&1; then
    claude mcp add context7 -- npx -y @upstash/context7-mcp
    echo "Added context7 MCP server"
else
    echo "MCP server context7 already configured, skipping"
fi

echo "MCP servers configured successfully."
echo "Run 'claude mcp list' to verify."
