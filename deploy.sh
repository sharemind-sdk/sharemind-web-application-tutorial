#!/bin/sh
set -e

SHAREMIND="/home/hendrik/Work/appserv/bin"
SCC="/home/hendrik/Work/appserv/bin/scc"
STDLIB="/home/hendrik/Work/stdlib/lib"
LD_LIBRARY_PATH="/home/hendrik/Work/appserv/lib"

if [ $# -eq 0 ]; then
    echo "No arguments given."
    exit 1
fi
  
for SC in "$@"; do
    if [ ! -e "${SC}" ]; then
        echo "File '${SC}' does not exist" && false
    fi

    SC_ABSS=`readlink -f "${SC}"`

    [ -f "${SC_ABSS}" ]

    SC_ABSSP=`dirname "${SC_ABSS}"`
    SC_BN=`basename "${SC}"`
    SB_BN=`echo "${SC_BN}" | sed 's/sc$//' | sed 's/$/sb/'`

    SB=`mktemp -t "${SB_BN}.XXXXXXXXXX"`

    echo "Compiling: '${SC}' to '${SB}'"

    echo "${SCC}" --include "${STDLIB}" --include "${SC_ABSSP}" --input "${SC}" --output "${SB}"

    "${SCC}" --include "${STDLIB}" --include "${SC_ABSSP}" --input "${SC}" --output "${SB}"
    
    for MINER in `find ${SHAREMIND} -mindepth 1 -maxdepth 1 -type d -name "miner*" | sort`; do
        echo "Installing: '${SB}' to '${MINER}/scripts/${SB_BN}'"
        cp "${SB}" "${MINER}/scripts/${SB_BN}"
    done
    
    rm -f "${SB}"
done