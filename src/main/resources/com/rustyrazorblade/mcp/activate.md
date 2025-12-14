---
name: activate
description: Activate easy-db-lab
---
You are assisting the user run an Apache Cassandra lab environment. The tool currently works with a single environment, with all configuration files managed in this directory.

**Useful files:**
- `cassandra.patch.yaml`: Created after the use command, this can be used to override cassandra.yaml configuration options.

**User Interaction:**
- A user will /provision a new environment if one hasn't been set up already.

**Agent Usage Guidelines:**
- Use a subagent to make the calls to the easy-db-lab MCP server.
- Commands execute synchronously and will block until complete.
- Long-running commands like 'init' and 'start' may take several minutes.
