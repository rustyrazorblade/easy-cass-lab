#!/usr/bin/env bash

alias nt="nodetool"
alias v="ls -lahG"
alias ts="tail -f -n1000 /mnt/cassandra/logs/system.log"
alias as="sudo tail -f /var/log/axonops/axon-agent.log"
alias l="cd /mnt/cassandra/logs"
alias d="cd /mnt/cassandra/data"
alias c="cqlsh $(hostname)"

export PATH="$PATH:/usr/share/bcc/tools:/usr/local/cassandra/current/bin:/usr/local/cassandra/current/tools/bin:/usr/local/async-profiler/bin:/usr/local/easy-cass-stress/bin"

# use ubuntu users's logs directory for nodetool commands
export CASSANDRA_LOG_DIR=/home/ubuntu/logs



