#!/usr/bin/env bash

echo "Configuring Cassandra monitoring"

EXPORTER_AGENT_VERSION="0.13.0"
EXPORTER_AGENT_JAR="jmx_prometheus_javaagent-${EXPORTER_AGENT_VERSION}.jar"

CASSANDRA_LIB="/usr/share/cassandra/lib"

curl -LO "https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/${EXPORTER_AGENT_VERSION}/jmx_prometheus_javaagent-${EXPORTER_AGENT_VERSION}.jar"

sudo mv ${EXPORTER_AGENT_JAR} ${CASSANDRA_LIB}/
sudo cp config.yaml /etc/cassandra/


echo "JVM_OPTS=\"\$JVM_OPTS -javaagent:${CASSANDRA_LIB}/${EXPORTER_AGENT_JAR}=9501:/etc/cassandra/config.yaml\"" | sudo tee -a /etc/cassandra/cassandra-env.sh
echo "Finished configuring Cassandra monitoring for the Cassandra process"

echo "Grabbing the node exporter for system metrics"

sudo useradd --no-create-home --shell /bin/false node_exporter

wget https://github.com/prometheus/node_exporter/releases/download/v0.17.0/node_exporter-0.17.0.linux-amd64.tar.gz

tar zxvf node_exporter*
sudo cp node_exporter*/node_exporter /usr/local/bin/

cat <<EOF | sudo tee /etc/systemd/system/node_exporter.service
[Unit]
Description=Node Exporter
Wants=network-online.target
After=network-online.target

[Service]
User=node_exporter
Group=node_exporter
Type=simple
ExecStart=/usr/local/bin/node_exporter

[Install]
WantedBy=multi-user.target

EOF

sudo systemctl daemon-reload
sudo systemctl start node_exporter
sudo systemctl status node_exporter

echo "Finished setting up node exporter"
