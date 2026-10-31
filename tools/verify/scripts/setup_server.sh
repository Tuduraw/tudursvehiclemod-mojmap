#!/bin/bash
# setup_server.sh <dir> <tudursvehiclemod jar> [port offset]
# Needs in $VT (default: this folder's parent "work" dir): dl/fabric-server-launch.jar, dl/fabric-api-*.jar,
# packs/ (addon packs to copy), cache/ (shared vanilla server files). The harness jar comes from ../harness/build/libs.
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
VT=${VT:-$HERE/../work}
D=$1; JAR=$2; OFF=${3:-0}
rm -rf "$D"; mkdir -p "$D/mods" "$D/tudursvehiclemod-addons"
cp $VT/dl/fabric-server-launch.jar "$D/"
cp $VT/dl/fabric-api-*.jar "$D/mods/"
cp $HERE/../harness/build/libs/tvmtest-1.0.0.jar "$D/mods/"
cp "$JAR" "$D/mods/"
cp -r "$VT"/packs/* "$D/tudursvehiclemod-addons/"
# Vanilla server jar + libraries are shared through a cache so they are downloaded once.
mkdir -p $VT/cache/libraries $VT/cache/versions
ln -s $VT/cache/libraries "$D/libraries"
ln -s $VT/cache/versions "$D/versions"
[ -f $VT/cache/server.jar ] && cp $VT/cache/server.jar "$D/server.jar"
echo "eula=true" > "$D/eula.txt"
cat > "$D/server.properties" <<'EOF'
online-mode=false
level-type=minecraft\:flat
level-seed=528103863299003651
generate-structures=false
spawn-protection=0
spawn-monsters=false
spawn-animals=false
difficulty=easy
view-distance=4
simulation-distance=4
max-tick-time=-1
enable-rcon=true
rcon.password=tvmtest
rcon.port=RCONPORT
server-port=GAMEPORT
sync-chunk-writes=true
pause-when-empty-seconds=0
EOF
sed -i "s/RCONPORT/$((25575+OFF))/; s/GAMEPORT/$((30000+OFF))/" "$D/server.properties"
