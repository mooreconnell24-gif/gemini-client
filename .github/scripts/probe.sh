#!/usr/bin/env bash
# Dumps signatures of selected Minecraft classes from the Loom cache, to help port between versions.
out=/tmp/out/probe.txt; mkdir -p /tmp/out; : > $out
MC=$(find ~/.gradle .gradle -name "*.jar" 2>/dev/null | grep -i minecraft | while read j; do unzip -l "$j" 2>/dev/null | grep -q "net/minecraft/client/Minecraft.class" && echo "$j"; done | head -1)
echo "MC jar: $MC" >> $out
[ -z "$MC" ] && exit 0
echo "== classes matching Ender|Swing|Sdl|Glfw|InputConstants" >> $out
unzip -l "$MC" | awk '{print $4}' | grep -E "Ender|Swing|InputConstants|[Ss]dl|[Gg]lfw|MouseHandler|KeyboardHandler" | grep -v '\$\$' >> $out
echo "== library jars (lwjgl/sdl)" >> $out
find ~/.gradle -name "*.jar" | grep -iE "lwjgl|sdl" | sed 's#.*/##' | sort -u >> $out
for c in "$@"; do echo "===== $c" >> $out; javap -p -cp "$MC" "$c" >> $out 2>&1; done
