#!/usr/bin/env bash
# Dev aid: prints the Mojang-named signatures of the Minecraft classes BetterSkies uses.
echo "--- candidate jars"
find ~/.gradle "$GITHUB_WORKSPACE/.gradle" -name '*.jar' 2>/dev/null | grep -i minecraft | grep -v sources | head -40
JAR=$(find ~/.gradle -path '*minecraftMaven/net/minecraft/minecraft-merged/*' -name '*.jar' 2>/dev/null | grep -v sources | head -n1)
echo "JAR=$JAR"
while read -r c; do
  [ -z "$c" ] && continue
  echo "=================== $c"
  javap -p -cp "$JAR" "$c" 2>&1
done < tools/api-classes.txt
MJ=$(find ~/.gradle -name 'mappings-layered*.jar' | head -n1)
unzip -o -q "$MJ" -d /tmp/mj
MAP=$(find /tmp/mj -name '*.tiny' | head -n3)
echo "MAP=$MAP"
for m in $MAP; do grep -E 'class_9975|class_9955|class_758\b|class_10799|field_56865|field_56875|field_56878|method_62302|method_62303|method_62304|method_62305|method_62306|method_62310|method_62168|method_62185' "$m" | head -80; done

echo "=================== SHADERS"
for f in position_tex_color.vsh position_tex_color.fsh position_tex.vsh position_tex.fsh position_color.fsh; do
  echo "--- $f"; unzip -p "$JAR" "assets/minecraft/shaders/core/$f" 2>&1 | head -60
done
echo "=================== PIPELINES"
cd "$GITHUB_WORKSPACE" && ./gradlew -q dumpPipelines 2>&1 | head -200

echo "=================== GAMETEST"
GT=$(find ~/.gradle -name 'fabric-client-gametest-api-v1*.jar' | grep -v sources | head -n1)
echo "GT=$GT"
unzip -l "$GT" | grep '\.class' | awk '{print $4}' | grep -v '/impl/\|mixin' | head -60
for c in $(unzip -l "$GT" | grep '\.class' | awk '{print $4}' | grep 'api/client/gametest' | grep -v '\$' | sed 's/\.class$//; s#/#.#g'); do
  echo "--- $c"; javap -cp "$GT:$JAR" "$c" 2>&1 | grep -v '^Compiled'
done
