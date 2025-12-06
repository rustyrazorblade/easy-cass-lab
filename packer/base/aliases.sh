#!/usr/bin/env bash

export PATH="$PATH:/usr/share/bcc/tools:/usr/local/cassandra/current/bin:/usr/local/cassandra/current/tools/bin:/usr/local/async-profiler/bin:/usr/local/cassandra-easy-stress/bin"
export ART="/mnt/db1/cassandra/artifacts"
export CASSANDRA_EASY_STRESS_LOG_DIR=/mnt/db1/cassandra/stress
export CASSANDRA_YAML="/usr/local/cassandra/current/conf/cassandra.yaml"
# use ubuntu users's logs directory for nodetool commands
export CASSANDRA_LOG_DIR=/home/ubuntu/logs

alias nt="nodetool"
alias v="ls -lahG"
alias ts="tail -f -n1000 /mnt/db1/cassandra/logs/system.log"
alias as="sudo tail -f /var/log/axonops/axon-agent.log"
alias l="cd /mnt/db1/cassandra/logs"
alias d="cd /mnt/db1/cassandra/data"
alias c="cqlsh $(hostname)"
alias drop-cache="echo 3 | sudo tee /proc/sys/vm/drop_caches"
alias heap-dump="sudo jmap -dump:live,format=b,file=/mnt/db1/cassandra/artifacts/heapdump-$(date +%s).hprof $(cassandra-pid)"
alias js="sudo jstack $(cassandra-pid) | tee /mnt/db1/cassandra/artifacts/jstack-$(date +%s).txt"
alias cdd="cd /mnt/db1/cassandra/data"
alias cdl="cd /mnt/db1/cassandra/logs"
alias cda="cd /mnt/db1/cassandra/artifacts"
alias cdc="cd /usr/local/cassandra/current/conf"

