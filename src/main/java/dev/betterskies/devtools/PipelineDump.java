package dev.betterskies.devtools;

import net.minecraft.client.renderer.RenderPipelines;

import java.lang.reflect.Field;

/** Dev aid (not shipped): prints how vanilla's sky pipelines are configured. */
public final class PipelineDump {
    public static void main(String[] args) throws Exception {
        for (String name : new String[]{"END_SKY", "CELESTIAL", "DEBUG_QUADS", "SKY", "STARS", "SUNRISE_SUNSET", "GUI_TEXTURED"}) {
            Object p = RenderPipelines.class.getField(name).get(null);
            System.out.println("== " + name);
            Class<?> c = p.getClass();
            while (c != null && c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    f.setAccessible(true);
                    System.out.println("   " + f.getName() + " = " + f.get(p));
                }
                c = c.getSuperclass();
            }
        }
    }
}
