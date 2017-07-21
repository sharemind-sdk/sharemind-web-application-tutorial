#!/bin/sh

if [ -n "${SHAREMIND_PATH}" ]; then
    export LD_LIBRARY_PATH="${SHAREMIND_PATH}/lib${LD_LIBRARY_PATH:+:}${LD_LIBRARY_PATH}"
fi

node gateway.js 1 &
node gateway.js 2 &
node gateway.js 3 &

wait
