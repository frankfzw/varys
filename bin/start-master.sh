#!/usr/bin/env bash

# Starts the master on the machine this script is executed on.

bin=`dirname "$0"`
bin=`cd "$bin"; pwd`

. "$bin/varys-config.sh"

if [ -f "${VARYS_CONF_DIR}/varys-env.sh" ]; then
  . "${VARYS_CONF_DIR}/varys-env.sh"
fi

if [ "$VARYS_MASTER_PORT" = "" ]; then
  VARYS_MASTER_PORT=1606
fi

if [ "$VARYS_MASTER_IP" = "" ]; then
  VARYS_MASTER_IP=`hostname`
fi

if [ "$VARYS_MASTER_WEBUI_PORT" = "" ]; then
  VARYS_MASTER_WEBUI_PORT=16016
fi

# Set VARYS_PUBLIC_DNS so the master report the correct webUI address to the slaves
if [ "$VARYS_PUBLIC_DNS" = "" ]; then
    # If we appear to be running on EC2, use the public address by default:
    # NOTE: ec2-metadata is installed on Amazon Linux AMI. Check based on that and hostname
    if command -v ec2-metadata > /dev/null || [[ `hostname` == *ec2.internal ]]; then
        export VARYS_PUBLIC_DNS=`wget -q -O - http://instance-data.ec2.internal/latest/meta-data/public-hostname`
    fi
fi

"$bin"/varys-daemon.sh start varys.VarysMaster
