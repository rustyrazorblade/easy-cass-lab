#!/usr/bin/env bash

echo "Installing Prometheus"

sudo useradd --no-create-home --shell /bin/false prometheus

ETC_DIR=/etc/prometheus
LIB_DIR=/var/lib/prometheus
BIN_DIR=/usr/local/bin/

sudo mkdir -p ${ETC_DIR}
sudo mkdir -p ${LIB_DIR}

PACKAGE_NAME=prometheus-2.7.1.linux-amd64
ARCHIVE_NAME=${PACKAGE_NAME}.tar.gz

curl -LO https://github.com/prometheus/prometheus/releases/download/v2.7.1/${ARCHIVE_NAME}

tar xvf ${ARCHIVE_NAME}

sudo cp ${PACKAGE_NAME}/prometheus ${BIN_DIR}
sudo cp ${PACKAGE_NAME}/promtool ${BIN_DIR}

sudo cp -r ${PACKAGE_NAME}/consoles ${ETC_DIR}
sudo cp -r ${PACKAGE_NAME}/console_libraries ${ETC_DIR}

# all configs
sudo cp config/prometheus/*.yml ${ETC_DIR}/

sudo cp config/prometheus/prometheus.service /etc/systemd/system/

sudo chown -R prometheus:prometheus ${ETC_DIR}
sudo chown -R prometheus:prometheus ${LIB_DIR}

rm -rf ${ARCHIVE_NAME} ${PACKAGE_NAME}