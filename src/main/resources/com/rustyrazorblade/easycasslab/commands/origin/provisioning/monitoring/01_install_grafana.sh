#!/usr/bin/env bash

set -x
echo "Installing Grafana"
export APT_KEY_DONT_WARN_ON_DANGEROUS_USAGE=true
export DEBIAN_FRONTEND=noninteractive

wget https://dl.grafana.com/oss/release/grafana_7.1.3_amd64.deb
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y ./grafana_7.1.3_amd64.deb

sudo cp config/grafana/grafana.ini /etc/grafana/

mkdir -p /etc/grafana/provisioning/datasources/
sudo cp config/grafana/datasource.yaml /etc/grafana/provisioning/datasources/
sudo chown root:grafana /etc/grafana/provisioning/datasources/datasource.yaml

sudo mkdir -p  /var/lib/grafana/dashboards

sudo cp config/grafana/dashboards.yaml /etc/grafana/provisioning/dashboards/
sudo cp dashboards/*.json /var/lib/grafana/dashboards

sudo chown -R grafana /var/lib/grafana/dashboards/

sudo /bin/systemctl daemon-reload
sudo /bin/systemctl enable grafana-server
