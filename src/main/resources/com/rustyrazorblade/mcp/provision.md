---
name: provision
description: Provision a cluster.
---
Ensure the /activate prompt is called prior to this.

If the user does not ask for stress nodes, get confirmation if they intended
to start a lab environment without a stress node.

This is the typical workflow for easy-cass-lab environments.  Follow the steps EXACTLY:

1. Initialize cluster: call 'init' with start: true (up is called automatically).
   Note: If the user doesn't specify a number of stress nodes, ask if they need a stress node.

   These are the most common parameters.  Use a drop down menus to pick from:

    - cassandraInstances (defaults to 3, or 1, 6)
    - stressInstances (usually 1, 0)
    - instanceType (EC2 Cassandra instance type (pick some common ones, or type my own))
    - stressInstanceType (EC2 Stress instance type, or type my own)

   Users may supply their own options for any of the Init parameters.

   Do not ask for a version.  This will be picked AFTER the nodes have started, since the choices are determined by what is available on the nodes.

   This command can take several minutes to complete.  Wait till it's finished before moving on.
2. Use the list command to get available Cassandra versions, then prompt the user to pick their Cassandra version, then call 'use' with the desired version.  Offer a drop down based on the results from the list command.
3. Check for configuration updates: review if any config changes are needed
4. If the configs need to be updated:
4.1. If yaml config changes are required, update cassandra.patch.yaml
4.2. JVM settings can be updated by editing the file under <CASSANDRA_VERSION>/jvm<JAVA_VERSION>-server.options.
     For example: 5.0/jvm17-server.options
4.3. Call 'update-config' to push these changes to all nodes.
5. Start the cluster with the start command.
6. Reconnect to the easy-cass-mcp and easy-cass-stress servers, as they will now be available.
7. Inform the user they can now run benchmarks and tests. These tools are available:

   easy-cass-mcp is available for executing queries against virtual tables.
   easy-cass-stress MCP server is available, if stress nodes were used.

IMPORTANT: Commands run asynchronously in the background to avoid timeouts.

- Each command returns immediately with a "started in background" message
- Use 'get_server_status' to monitor progress and see accumulated log messages
- Call 'get_server_status' once a second until status shows 'idle' before proceeding
- Use a dedicated sub-agent to call get_server_status (if possible)
- Long-running commands (especially 'up' and 'start') may take several minutes

When done with the cluster, call the down tool and ALWAYS set autoApprove to true.


When done, call down with autoApprove: true to shut the cluster down.
