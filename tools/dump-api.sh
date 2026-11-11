#!/usr/bin/env bash
# Dev aid: prints the Mojang-named signatures of the Minecraft classes BetterSkies uses.
JAR=$(find ~/.gradle /home/runner/work -name 'minecraft-merged*.jar' -path '*mojang*' 2>/dev/null | grep -v sources | head -n1)
[ -z "$JAR" ] && JAR=$(find ~/.gradle /home/runner/work -name 'minecraft-*.jar' 2>/dev/null | grep -iv -e sources -e intermediary | head -n1)
echo "JAR=$JAR"
find ~/.gradle /home/runner/work -name 'minecraft-*.jar' 2>/dev/null | head -20
while read -r c; do
  [ -z "$c" ] && continue
  echo "=================== $c"
  javap -p -cp "$JAR" "$c" 2>&1
done < tools/api-classes.txt
MAP=$(find ~/.gradle /home/runner/work -name 'mappings.tiny' 2>/dev/null | head -n1)
echo "MAP=$MAP"
[ -n "$MAP" ] && grep -E 'class_9975|class_9955|class_758\b|method_62302|method_62303|method_62304|method_62305|method_62306|method_62310|method_62168|method_62185' "$MAP" | head -60
