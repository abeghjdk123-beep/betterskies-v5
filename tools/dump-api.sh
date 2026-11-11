#!/usr/bin/env bash
# Dev aid: prints the Mojang-named signatures of the Minecraft classes BetterSkies uses.
echo "--- candidate jars"
find ~/.gradle "$GITHUB_WORKSPACE/.gradle" -name '*.jar' 2>/dev/null | grep -i minecraft | grep -v sources | head -40
JAR=$(find ~/.gradle "$GITHUB_WORKSPACE/.gradle" -name '*.jar' 2>/dev/null | grep -i 'minecraft-merged\|minecraft-client' | grep -iv -e sources -e intermediary -e unpick -e named-unmapped | head -n1)
echo "JAR=$JAR"
while read -r c; do
  [ -z "$c" ] && continue
  echo "=================== $c"
  javap -p -cp "$JAR" "$c" 2>&1
done < tools/api-classes.txt
MAP=$(find ~/.gradle "$GITHUB_WORKSPACE/.gradle" -name '*.tiny' 2>/dev/null | head -n5)
echo "MAP=$MAP"
for m in $MAP; do grep -E 'class_9975|class_9955|class_758\b|class_10799|field_56865|field_56875|field_56878|method_62302|method_62303|method_62304|method_62305|method_62306|method_62310|method_62168|method_62185' "$m" | head -80; done
