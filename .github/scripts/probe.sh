#!/usr/bin/env bash
# Dumps signatures (and bytecode for mixin targets) of Minecraft classes from the Loom cache, to help port between versions.
mkdir -p /tmp/out/classes; out=/tmp/out/probe.txt; : > $out
MC=$(find ~/.gradle/caches/fabric-loom -name "minecraft-client.jar" 2>/dev/null | head -1)
[ -z "$MC" ] && MC=$(find ~/.gradle -name "*.jar" 2>/dev/null | grep -i minecraft | while read j; do unzip -l "$j" 2>/dev/null | grep -q "net/minecraft/client/Minecraft.class" && echo "$j"; done | head -1)
echo "MC jar: $MC" >> $out
[ -z "$MC" ] && exit 0
unzip -l "$MC" | awk '{print $4}' | grep '\.class$' > /tmp/out/class-list.txt
while read c; do [ -z "$c" ] && continue; javap -p -constants -cp "$MC" "$c" > "/tmp/out/classes/$c.txt" 2>&1; done < .github/scripts/probe-classes.txt
while read c; do [ -z "$c" ] && continue; javap -p -c -cp "$MC" "$c" > "/tmp/out/classes/$c.bytecode.txt" 2>&1; done < .github/scripts/mixin-targets.txt
