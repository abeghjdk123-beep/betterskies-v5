package dev.betterskies.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Every call into Minecraft's GPU API lives here. BetterSkies draws with two of
 * the game's own sky pipelines: END_SKY (textured, vertex colors, alpha blended)
 * and CELESTIAL (textured, additive), so it needs no shaders of its own.
 */
public final class Gpu {
    private Gpu() {
    }

    private static final Vector3f NO_OFFSET = new Vector3f();
    private static final Matrix4f NO_TEXTURE_MATRIX = new Matrix4f();
    private static GpuSampler clamp;
    private static GpuSampler wrap;

    public static RenderPipeline alpha() {
        return RenderPipelines.END_SKY;
    }

    public static RenderPipeline additive() {
        return RenderPipelines.CELESTIAL;
    }

    public static GpuSampler sampler(boolean wrapU) {
        if (clamp == null) {
            clamp = RenderSystem.getSamplerCache().getSampler(
                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);
            wrap = RenderSystem.getSamplerCache().getSampler(
                    AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);
        }
        return wrapU ? wrap : clamp;
    }

    public static Matrix4f camera() {
        return new Matrix4f(RenderSystem.getModelViewMatrix());
    }

    // ================================================================== meshes

    public static final class Mesh implements AutoCloseable {
        final GpuBuffer buffer;
        final int indexCount;
        final RenderPipeline pipeline;

        Mesh(GpuBuffer buffer, int indexCount, RenderPipeline pipeline) {
            this.buffer = buffer;
            this.indexCount = indexCount;
            this.pipeline = pipeline;
        }

        @Override
        public void close() {
            buffer.close();
        }
    }

    /** Uploads geometry laid out for a pipeline; null if empty. */
    public static Mesh upload(Geometry g, RenderPipeline pipeline, String label) {
        int verts = g.vertexCount();
        if (verts < 4) {
            return null;
        }
        VertexFormat format = pipeline.getVertexFormat();
        VertexFormat.Mode mode = pipeline.getVertexFormatMode();
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(Math.max(256, verts * format.getVertexSize()))) {
            BufferBuilder bb = new BufferBuilder(bytes, mode, format);
            for (int i = 0; i < verts; i++) {
                bb.addVertex(g.x(i), g.y(i), g.z(i)).setUv(g.u(i), g.v(i)).setColor(g.argb(i));
            }
            try (MeshData mesh = bb.buildOrThrow()) {
                GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "BetterSkies " + label,
                        GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
                return new Mesh(buffer, mesh.drawState().indexCount(), pipeline);
            }
        }
    }

    /** Draws a mesh with a texture; tint multiplies every pixel (rgb, alpha). */
    public static void draw(Mesh mesh, Matrix4fc modelView, Vector4f tint, GpuTextureView texture, GpuSampler sampler) {
        if (mesh == null || texture == null) {
            return;
        }
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .writeTransform(modelView, tint, NO_OFFSET, NO_TEXTURE_MATRIX);
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(mesh.pipeline.getVertexFormatMode());
        GpuBuffer indexBuffer = indices.getBuffer(mesh.indexCount);
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "BetterSkies", target.getColorTextureView(), OptionalInt.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(mesh.pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("Sampler0", texture, sampler);
            pass.setVertexBuffer(0, mesh.buffer);
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(0, 0, mesh.indexCount, 1);
        }
    }

    /** Uploads, draws and frees geometry that changes every frame. */
    public static void drawOnce(Geometry g, RenderPipeline pipeline, Matrix4fc modelView, Vector4f tint,
                                GpuTextureView texture, GpuSampler sampler, String label) {
        if (g.isEmpty()) {
            return;
        }
        Mesh m = upload(g, pipeline, label);
        if (m != null) {
            try {
                draw(m, modelView, tint, texture, sampler);
            } finally {
                m.close();
            }
        }
    }

    // ================================================================== textures

    public static final class Tex implements AutoCloseable {
        private final DynamicTexture texture;
        public final int width;
        public final int height;
        private Identifier id;

        Tex(DynamicTexture texture, int width, int height) {
            this.texture = texture;
            this.width = width;
            this.height = height;
        }

        public GpuTextureView view() {
            return texture.getTextureView();
        }

        /** Replaces the pixels (same size). */
        public void update(int[] argb) {
            NativeImage img = texture.getPixels();
            if (img == null) {
                return;
            }
            fill(img, argb, width, height);
            texture.upload();
        }

        /** Registers the texture so GUI code can blit it; returns its id. */
        public Identifier gui(String path) {
            if (id == null) {
                id = Identifier.fromNamespaceAndPath("betterskies", path);
                Minecraft.getInstance().getTextureManager().register(id, texture);
            }
            return id;
        }

        @Override
        public void close() {
            if (id != null) {
                Minecraft.getInstance().getTextureManager().release(id);
                id = null;
            } else {
                texture.close();
            }
        }
    }

    /** Creates a texture from ARGB pixels (render thread only). */
    public static Tex texture(String label, int[] argb, int w, int h) {
        NativeImage img = new NativeImage(w, h, false);
        fill(img, argb, w, h);
        DynamicTexture tex = new DynamicTexture(() -> "BetterSkies " + label, img);
        return new Tex(tex, w, h);
    }

    private static void fill(NativeImage img, int[] argb, int w, int h) {
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                img.setPixel(x, y, argb[row + x]);
            }
        }
    }
}
