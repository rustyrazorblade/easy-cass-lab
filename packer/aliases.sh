#!/usr/bin/env bash

alias v="ls -lahG"

export PATH="$PATH:/usr/share/bcc/tools:/usr/local/cassandra/current/bin:/usr/local/cassandra/current/tools/bin:/usr/local/async-profiler/bin:/usr/local/easy-cass-stress/bin"

# use ubuntu users's logs directory for nodetool commands
export CASSANDRA_LOG_DIR=/home/ubuntu/logs

c() {
  cqlsh $(hostname)
}

l() {
  cd /mnt/cassandra/logs
}

ts() {
  tail -f /mnt/cassandra/logs/system.log
}