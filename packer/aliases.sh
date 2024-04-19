#!/usr/bin/env bash

alias nt="nodetool"
alias v="ls -lahG"
alias ts="tail -f -n1000 /mnt/cassandra/logs/system.log"
alias as="sudo tail -f /var/log/axonops/axon-agent.log"
alias l="cd /mnt/cassandra/logs"
alias d="cd /mnt/cassandra/data"
alias c="cqlsh $(hostname)"
alias drop-cache="echo 3 | sudo tee /proc/sys/vm/drop_caches"
alias heap-dump="sudo jmap -dump:live,format=b,file=/mnt/cassandra/artifacts/heapdump-$(date +%s).hprof $(cassandra-pid)"
alias js="sudo jstack $(cassandra-pid) | tee /mnt/cassandra/artifacts/jstack-$(date +%s).txt"
alias cdd="cd /mnt/cassandra/data"
alias cdl="cd /mnt/cassandra/logs"
alias cda="cd /mnt/cassandra/artifacts"
alias cdc="cd /usr/local/cassandra/current/conf"

export PATH="$PATH:/usr/share/bcc/tools:/usr/local/cassandra/current/bin:/usr/local/cassandra/current/tools/bin:/usr/local/async-profiler/bin:/usr/local/easy-cass-stress/bin"
export ART="/mnt/cassandra/artifacts"
export CASSANDRA_YAML="/usr/local/cassandra/current/cassandra.yaml"
# use ubuntu users's logs directory for nodetool commands
export CASSANDRA_LOG_DIR=/home/ubuntu/logs



