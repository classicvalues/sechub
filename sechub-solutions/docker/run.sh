#!/usr/bin/env sh
# SPDX-License-Identifier: MIT

JAVA_DEBUG_OPTIONS=""

wait_loop() {
    while true
    do
	    echo "Press [CTRL+C] to stop.."
	    sleep 120
    done
}

localserver() {
    storage_options="-Dsechub.storage.sharedvolume.upload.dir=$SECHUB_STORAGE_SHAREDVOLUME_UPLOAD_DIR"
    profiles="dev,h2,real_products"

    java $JAVA_DEBUG_OPTIONS \
        $storage_options \
        -Dfile.encoding=UTF-8 \
        -Dspring.profiles.active="$profiles" \
        -Dsechub.targettype.detection.intranet.hostname.endswith=intranet.example.org \
        -Dsechub.config.trigger.nextjob.initialdelay=0 \
        -Dsechub.initialadmin.userid=sechubadm \
        -Dsechub.initialadmin.email=sechubadm@example.org \
        -Dsechub.initialadmin.apitoken=myTop$ecret! \
        -Dsechub.adapter.netsparker.userid=abc \
        -Dsechub.adapter.netsparker.apitoken=xyz \
        -Dsechub.adapter.netsparker.baseurl=https://example.org \
        -Dsechub.adapter.netsparker.defaultpolicyid=example \
        -Dsechub.adapter.netsparker.licenseid=example \
        -Dsechub.adapter.nessus.defaultpolicyid=example \
        -Dsechub.notification.email.administrators=example@example.org \
        -Dsechub.notification.email.from=example@example.org \
        -Dsechub.notification.smtp.hostname=example.org \
        -Dserver.port=8443 \
        -Dserver.address=0.0.0.0 \
        -jar sechub-server*.jar
}

debug () {
    wait_loop
}

if [ "$JAVA_ENABLE_DEBUG" = "true" ]
then
    # By using `address=*:15024` the server will bind 
    # all available IP addresses to port 15024
    # otherwise the container cannot be accessed from outside
    JAVA_DEBUG_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,address=*:15024"
fi

if [ "$SECHUB_START_MODE" = "localserver" ]
then
    localserver
else
    debug
fi