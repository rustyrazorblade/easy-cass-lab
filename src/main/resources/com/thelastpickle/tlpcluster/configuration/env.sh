#@IgnoreInspection BashAddShebang

YELLOW='\033[0;33m'
YELLOW_BOLD='\033[1;33m'
NC_BOLD='\033[1m'
NC='\033[0m' # No Color

echo -e "${YELLOW_BOLD}[WARNING]${YELLOW} We are creating aliases which override these commands:${NC}"
echo -e "${NC_BOLD}  ssh\n  sftp\n  scp\n  rsync\n${NC}"
echo "The aliases point the commands they override to your new cluster."
echo -e "To undo these changes exit this terminal.\n"


SSH_CONFIG="$(pwd)/sshConfig"
alias ssh="ssh -F $SSH_CONFIG"
alias sftp="sftp -F $SSH_CONFIG"
alias scp="scp -F $SSH_CONFIG"
alias rsync="rsync -ave 'ssh -F $SSH_CONFIG'"

# general purpose function for executing commands on all cassandra nodes
c-all () {
    for i in "${SERVERS[@]}"
    do
        ssh $i $@
    done
}

alias c-restart="c-all sudo systemctl restart cassandra.service"
alias c-status="c0 nodetool status"
alias c-tpstats="c-all nodetool tpstats"

c-collect-artifacts() {
    NAME="$1"
    if [ -z "$NAME" ]
    then
        echo "name required"
        return
    fi

    ARTIFACT_DIR="artifacts/$NAME"
    mkdir -p $ARTIFACT_DIR
    
    echo $NAME > $ARTIFACT_DIR/issue.txt

    for i in "${SERVERS[@]}"
    do
        echo "Collecting $i"
        NODE_ARTIFACT_DIR=$ARTIFACT_DIR/extracted/$i
        (
            mkdir -p $NODE_ARTIFACT_DIR
            cd $NODE_ARTIFACT_DIR
            mkdir nodetool os storage cloud conf

            #schema
            ssh $i 'cqlsh $(hostname) -e "DESC SCHEMA"' > schema.cql

            # ndoetool
            ssh $i 'nodetool status' > nodetool/status.txt
            ssh $i 'nodetool tablestats' > nodetool/cfstats.txt
            ssh $i 'nodetool describecluster' > nodetool/describecluster.txt
            ssh $i 'nodetool info' > nodetool/info.txt
            ssh $i 'nodetool version' > nodetool/version.txt
            ssh $i 'nodetool tpstats' > nodetool/tpstats.txt
            ssh $i 'nodetool proxyhistograms' > nodetool/proxyhistograms.txt
            ssh $i 'nodetool netstats' > nodetool/netstats.txt

            # other OS
            ssh $i 'sar -A' > os/sar.txt
            ssh $i 'sudo blockdev --report' > os/blockdev-report.txt
            ssh $i 'hostname' > os/hostname.txt
            ssh $i 'cat /proc/meminfo' > os/meminfo
            ssh $i 'cat /proc/cpuinfo' > os/cpuinfo
            ssh $i 'lscpu' > os/lscpu.txt

            # configs
            rsync $i:/etc/cassandra/ ./conf/
            rsync $i:/var/log/cassandra/ ./logs/

            # JMX
            ssh $i -C 'cd provisioning/cassandra; java -cp collector-0.11.1-SNAPSHOT.jar io.prometheus.jmx.JmxScraper service:jmx:rmi:///jndi/rmi://127.0.0.1:7199/jmxrmi' > metrics.jmx
        )
    done
}

alias c-start="c-all sudo systemctl start cassandra.service"
alias c-df="c-all df -h | grep -E 'cassandra|Filesystem'"

c-flame() {
  HOST=$1
  OUTPUT=$2

  [ -z "$HOST" ] || [ -z "$OUTPUT" ] && echo "Host and output path is required"

  ssh $HOST -C 'cd provisioning/cassandra/profiler; sudo ./profiler.sh -d 30 -f /tmp/flamegraph.svg $(cat /var/run/cassandra/cassandra.pid )'
  scp $HOST:/tmp/flamegraph.svg $OUTPUT
  ssh $HOST -C 'sudo rm /tmp/flamegraph.svg'

}

c-flame-alloc() {
  HOST=$1
  OUTPUT=$2

  [ -z "$HOST" ] || [ -z "$OUTPUT" ] && echo "Host and output path is required"

  ssh $HOST -C 'cd provisioning/cassandra/profiler; sudo ./profiler.sh -e alloc -d 30 -f /tmp/flamegraph.svg $(cat /var/run/cassandra/cassandra.pid )'
  scp $HOST:/tmp/flamegraph.svg $OUTPUT
  ssh $HOST -C 'sudo rm /tmp/flamegraph.svg'

}

