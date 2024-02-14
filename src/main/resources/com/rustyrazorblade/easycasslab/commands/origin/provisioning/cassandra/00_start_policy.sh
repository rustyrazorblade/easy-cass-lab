#!/usr/bin/env bash

echo "Setting up policy to disable Cassandra starting automatically"

cat > ./policy-rc.d << EOF
#!/bin/sh
echo "All runlevel operations denied by policy" >&2
exit 101
EOF

POLICY_RC_PATH="/usr/sbin/policy-rc.d"

sudo mv policy-rc.d ${POLICY_RC_PATH}
sudo chown root:root ${POLICY_RC_PATH}
sudo chmod 755 ${POLICY_RC_PATH}