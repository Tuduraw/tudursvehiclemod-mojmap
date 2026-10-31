#!/bin/bash
# run_server.sh <dir> <name>  (LIMIT=n runs only the first n vehicles)  - full scenario, park, restart, parkcheck
D=$1; N=$2
PORT=$(grep rcon.port "$D/server.properties" | cut -d= -f2)
R="python3 $(cd "$(dirname "$0")" && pwd)/rcon.py --port $PORT"
cd "$D"
waitlog() { # pattern, timeout seconds
  local t=0; until grep -q "$1" logs/latest.log 2>/dev/null; do sleep 3; t=$((t+3)); if [ $t -gt $2 ]; then echo "TIMEOUT waiting for $1"; return 1; fi; if ! kill -0 $PID 2>/dev/null; then echo "SERVER DIED"; return 1; fi; done; }
start() { rm -f logs/latest.log; java -Xmx3G -jar fabric-server-launch.jar nogui > console-$1.log 2>&1 & PID=$!; waitlog 'Done (' 900 || exit 1; [ -f server.jar ] && [ ! -f ${VT:-$(cd "$(dirname "$0")" && pwd)/../work}/cache/server.jar ] && cp server.jar ${VT:-$(cd "$(dirname "$0")" && pwd)/../work}/cache/; }
stop() { $R "save-all flush" "stop" >/dev/null; wait $PID; }

start 1
$R "gamerule doMobSpawning false" "gamerule doDaylightCycle false" "gamerule doWeatherCycle false" "time set 6000" >/dev/null
$R "tvmtest run $N ${LIMIT:-99}" ; waitlog "DONE $N " 5400 || exit 1
$R "tvmtest park $N-park" ; waitlog "DONE $N-park " 600 || exit 1
cp logs/latest.log log-run.log
stop
start 2
$R "tvmtest parkcheck $N-parkcheck" ; waitlog "DONE $N-parkcheck " 600 || exit 1
cp logs/latest.log log-restart.log
stop
echo "FINISHED $N"
