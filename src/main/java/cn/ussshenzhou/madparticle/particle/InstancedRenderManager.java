package cn.ussshenzhou.madparticle.particle;

import cn.ussshenzhou.madparticle.MadParticleConfig;
import cn.ussshenzhou.t88.T88;
import cn.ussshenzhou.t88.config.ConfigHelper;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.ArrayUtils;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * @author USS_Shenzhou
 */
@SuppressWarnings("MapOrSetKeyShouldOverrideHashCodeEquals")
public class InstancedRenderManager {
    public static final int INSTANCE_UV_INDEX = 2;
    public static final int INSTANCE_COLOR_INDEX = 3;
    public static final int INSTANCE_UV2_INDEX = 4;
    public static final int INSTANCE_MATRIX_INDEX = 5;

    public static final int SIZE_FLOAT_OR_INT_BYTES = 4;
    public static final int AMOUNT_MATRIX_FLOATS = 4 * 4;
    public static final int AMOUNT_INSTANCE_FLOATS = 4 + 4 + (2 + 2) + AMOUNT_MATRIX_FLOATS;
    public static final int SIZE_INSTANCE_BYTES = AMOUNT_INSTANCE_FLOATS * SIZE_FLOAT_OR_INT_BYTES;

    private static int threads = Mth.clamp(ConfigHelper.getConfigRead(MadParticleConfig.class).bufferFillerThreads, 1, Integer.MAX_VALUE);
    @SuppressWarnings("unchecked")
    private static LinkedHashSet<TextureSheetParticle>[] PARTICLES = Stream.generate(() -> Sets.newLinkedHashSetWithExpectedSize(32768)).limit(threads).toArray(LinkedHashSet[]::new);
    private static Executor fixedThreadPool = Executors.newFixedThreadPool(threads);
    @SuppressWarnings("unchecked")
    private static HashMap<Long, Integer>[] LIGHT_CACHE = Stream.generate(() -> new HashMap<Long, Integer>(16384)).limit(threads).toArray(HashMap[]::new);

    @SuppressWarnings("unchecked")
    public static void setThreads(int amount) {
        if (amount <= 0 || amount > 128) {
            throw new IllegalArgumentException("The amount of auxiliary threads should between 1 and 128. Correct the config file manually.");
        }
        threads = amount;
        PARTICLES = Stream.generate(() -> Sets.newLinkedHashSetWithExpectedSize(32768)).limit(threads).toArray(LinkedHashSet[]::new);
        fixedThreadPool = Executors.newFixedThreadPool(threads);
        LIGHT_CACHE = Stream.generate(() -> new HashMap<String, Integer>(16384)).limit(threads).toArray(HashMap[]::new);
    }

    public static Executor getFixedThreadPool() {
        return fixedThreadPool;
    }

    public static int getThreads() {
        return threads;
    }

    private static HashSet<TextureSheetParticle> findSmallestSet() {
        HashSet<TextureSheetParticle> r = PARTICLES[0];
        int minSize = r.size();
        for (int i = 1; i < threads; i++) {
            if (PARTICLES[i].size() < minSize) {
                r = PARTICLES[i];
                minSize = r.size();
            }
        }
        return r;
    }

    public static void add(TextureSheetParticle particle) {
        findSmallestSet().add(particle);
    }

    public static void reload(Collection<Particle> particles) {
        clear();
        particles.forEach(p -> add((TextureSheetParticle) p));
    }

    public static void remove(TextureSheetParticle particle) {
        for (int i = 0; i < threads; i++) {
            if (PARTICLES[i].remove(particle)) {
                break;
            }
        }
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    public static void removeAll(Collection<Particle> particle) {
        //Arrays.stream(PARTICLES).forEach(set -> set.removeAll(particle));
        particle.stream().filter(p -> p instanceof TextureSheetParticle).forEach(p -> remove((TextureSheetParticle) p));
    }

    public static void clear() {
        Arrays.stream(PARTICLES).forEach(HashSet::clear);
    }

    public static int amount() {
        int size = 0;
        for (int i = 0; i < threads; i++) {
            size += PARTICLES[i].size();
        }
        return size;
    }

    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, LightTexture lightTexture, Camera camera, float partialTicks, Frustum clippingHelper, TextureManager textureManager) {
        if (amount() == 0) {
            return;
        }
        InstancedRenderBufferBuilder bufferBuilder = ModParticleRenderTypes.instancedRenderBufferBuilder;
        ModParticleRenderTypes.INSTANCED.begin(bufferBuilder, textureManager);
        //ByteBuffer instanceMatrixBuffer = MemoryUtil.memAlloc(PARTICLES.size() * SIZE_INSTANCE_BYTES);
        //MemoryUtil.memSet(instanceMatrixBuffer, 0);
        ByteBuffer instanceMatrixBuffer = MemoryUtil.memCalloc(amount(), SIZE_INSTANCE_BYTES);
        int amount;
        //TODO add an option of checking visibility
        if (threads <= 1) {
            amount = renderSync(instanceMatrixBuffer, camera, partialTicks, clippingHelper);
        } else {
            amount = renderAsync(instanceMatrixBuffer, camera, partialTicks, clippingHelper);
        }
        fillVertices(bufferBuilder);
        BufferBuilder.RenderedBuffer renderedBuffer = bufferBuilder.end();
        var vertexBuffer = BufferUploader.upload(renderedBuffer);
        if (cn.ussshenzhou.madparticle.MadParticle.IS_OPTIFINE_INSTALLED) {
            //noinspection DataFlowIssue
            GL30C.glBindVertexArray(vertexBuffer.arrayObjectId);
            GL20C.glEnableVertexAttribArray(1);
            GL30C.glVertexAttribIPointer(1, 4, GL11C.GL_INT, 28, 3 * 4);
        }
        //noinspection DataFlowIssue
        int instanceMatrixBufferId = bindBuffer(instanceMatrixBuffer, vertexBuffer.arrayObjectId);
        ShaderInstance shader = RenderSystem.getShader();
        prepare(shader);
        GL31C.glDrawElementsInstanced(4, 6,
                RenderSystem.sharedSequentialQuad.hasStorage(65536) ? GL11C.GL_UNSIGNED_INT : GL11C.GL_UNSIGNED_SHORT,
                0, amount);
        //noinspection DataFlowIssue
        shader.clear();
        end(instanceMatrixBuffer, instanceMatrixBufferId);
        Arrays.stream(LIGHT_CACHE).forEach(HashMap::clear);
    }

    @SuppressWarnings("unchecked")
    public static int renderAsync(ByteBuffer instanceMatrixBuffer, Camera camera, float partialTicks, Frustum clippingHelper) {
        var camPosCompensate = camera.getPosition().toVector3f().mul(-1);
        CompletableFuture<Void>[] futures = new CompletableFuture[threads];
        for (int i = 0; i < threads; i++) {
            int finalI = i;
            futures[i] = CompletableFuture.runAsync(
                    () -> partial(finalI, instanceMatrixBuffer, partialTicks, camera, camPosCompensate, clippingHelper),
                    fixedThreadPool
            );
        }
        CompletableFuture.allOf(futures).join();
        return amount();
    }

    private static void partial(int group, ByteBuffer buffer, float partialTicks, Camera camera, Vector3f camPosCompensate, Frustum clippingHelper) {
        HashMap<Long, Integer> lightCache = LIGHT_CACHE[group];
        Matrix4f matrix4f = new Matrix4f();
        var simpleBlockPosSingle = new SimpleBlockPos(0, 0, 0);
        var set = PARTICLES[group];
        int index = 0;
        for (int i = 0; i < group; i++) {
            index += PARTICLES[i].size();
        }
        for (TextureSheetParticle particle : set) {
            fillBuffer(lightCache, buffer, particle, index, partialTicks, matrix4f, camera, camPosCompensate, simpleBlockPosSingle);
            index++;
        }
    }

    public static int renderSync(ByteBuffer instanceMatrixBuffer, Camera camera, float partialTicks, Frustum clippingHelper) {
        Matrix4f matrix4f = new Matrix4f();
        var camPosCompensate = camera.getPosition().toVector3f().mul(-1);
        var simpleBlockPosSingle = new SimpleBlockPos(0, 0, 0);
        int amount = 0;
        for (TextureSheetParticle particle : PARTICLES[0]) {
            if (clippingHelper != null && particle.shouldCull() && !clippingHelper.isVisible(particle.getBoundingBox())) {
                continue;
            }
            fillBuffer(LIGHT_CACHE[threads - 1], instanceMatrixBuffer, particle, amount, partialTicks, matrix4f, camera, camPosCompensate, simpleBlockPosSingle);
            amount++;
        }
        return amount;
    }

    private static int getLight(TextureSheetParticle particle, BlockPos pos) {
        return particle.level.hasChunkAt(pos) ? LevelRenderer.getLightColor(particle.level, pos) : 0;
    }

    /**
     * HOTSPOT
     * Can you find a way to make it faster?
     */
    public static void fillBuffer(HashMap<Long, Integer> lightCache, ByteBuffer buffer, TextureSheetParticle particle, int index, float partialTicks, Matrix4f matrix4fSingle, Camera camera, Vector3f camPosCompensate, SimpleBlockPos simpleBlockPosSingle) {
        int start = index * SIZE_INSTANCE_BYTES;
        //uv
        var sprite = particle.sprite;
        buffer.putFloat(start, sprite.getU0());
        buffer.putFloat(start + 4, sprite.getU1());
        buffer.putFloat(start + 4 * 2, sprite.getV0());
        buffer.putFloat(start + 4 * 3, sprite.getV1());
        //color
        buffer.putFloat(start + 4 * 4, particle.rCol);
        buffer.putFloat(start + 4 * 5, particle.gCol);
        buffer.putFloat(start + 4 * 6, particle.bCol);
        buffer.putFloat(start + 4 * 7, particle.alpha);
        //uv2
        float x = Mth.lerp(partialTicks, (float) particle.xo, (float) particle.x);
        float y = Mth.lerp(partialTicks, (float) particle.yo, (float) particle.y);
        float z = Mth.lerp(partialTicks, (float) particle.zo, (float) particle.z);
        simpleBlockPosSingle.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        Long pos = simpleBlockPosSingle.asLong();
        int l;
        if (particle instanceof MadParticle madParticle) {
            Integer l1 = lightCache.get(pos);
            if (l1 != null) {
                l = l1;
            } else {
                l = getLight(particle, new BlockPos(simpleBlockPosSingle.x, simpleBlockPosSingle.y, simpleBlockPosSingle.z));
                lightCache.put(pos, l);
            }
            l = madParticle.checkEmit(l);
        } else if (!TakeOver.RENDER_CUSTOM_LIGHT.contains(particle.getClass())) {
            Integer l1 = lightCache.get(pos);
            if (l1 != null) {
                l = l1;
            } else {
                l = getLight(particle, new BlockPos(simpleBlockPosSingle.x, simpleBlockPosSingle.y, simpleBlockPosSingle.z));
                lightCache.put(pos, l);
            }
        } else {
            l = particle.getLightColor(partialTicks);
        }
        buffer.putInt(start + 4 * 8, l & 0x0000_ffff);
        buffer.putInt(start + 4 * 9, l >> 16 & 0x0000_ffff);
        //matrix
        matrix4fSingle.identity().translation(x + camPosCompensate.x, y + camPosCompensate.y, z + camPosCompensate.z)
                .rotate(camera.rotation())
                .scale(particle.getQuadSize(partialTicks));
        var r = Mth.lerp(partialTicks, particle.oRoll, particle.roll);
        if (r != 0) {
            matrix4fSingle.rotateZ(r);
        }
        matrix4fSingle.get(start + 4 * 12, buffer);
    }

    public static void fillVertices(InstancedRenderBufferBuilder bufferBuilder) {
        bufferBuilder.vertex(-1, -1, 0);
        bufferBuilder.uvControl(0, 1, 0, 1).endVertex();

        bufferBuilder.vertex(-1, 1, 0);
        bufferBuilder.uvControl(0, 1, 1, 0).endVertex();

        bufferBuilder.vertex(1, 1, 0);
        bufferBuilder.uvControl(1, 0, 1, 0).endVertex();

        bufferBuilder.vertex(1, -1, 0);
        bufferBuilder.uvControl(1, 0, 0, 1).endVertex();
    }

    public static void prepare(ShaderInstance shader) {
        for (int i1 = 0; i1 < 12; ++i1) {
            int textureId = RenderSystem.getShaderTexture(i1);
            shader.setSampler("Sampler" + i1, textureId);
        }

        if (shader.MODEL_VIEW_MATRIX != null) {
            shader.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        }

        if (shader.PROJECTION_MATRIX != null) {
            shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        }

        if (shader.COLOR_MODULATOR != null) {
            shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        }

        if (shader.FOG_START != null) {
            shader.FOG_START.set(RenderSystem.getShaderFogStart());
        }

        if (shader.FOG_END != null) {
            shader.FOG_END.set(RenderSystem.getShaderFogEnd());
        }

        if (shader.FOG_COLOR != null) {
            shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        }

        if (shader.FOG_SHAPE != null) {
            shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        }

        RenderSystem.setupShaderLights(shader);
        shader.apply();
        if (cn.ussshenzhou.madparticle.MadParticle.IS_OPTIFINE_INSTALLED) {
            //TODO if optifine shader using
            GL20C.glUseProgram(shader.getId());
        }
        if (cn.ussshenzhou.madparticle.MadParticle.irisOn) {
            //Borrow iris particle shader's frame buffer.
            //Profiler tells me this is ok. We should trust JVM.
            try {
                ShaderInstance translucent = GameRenderer.getParticleShader();
                Class<? extends ShaderInstance> translucentClass = translucent.getClass();
                Field writingToAfterTranslucent = translucentClass.getDeclaredField("writingToAfterTranslucent");
                writingToAfterTranslucent.setAccessible(true);
                Object irisGlFramebuffer = writingToAfterTranslucent.get(translucent);
                Field id = irisGlFramebuffer.getClass().getSuperclass().getDeclaredField("id");
                id.setAccessible(true);
                int frameBuffer = (int) id.get(irisGlFramebuffer);
                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, frameBuffer);
            } catch (Exception e) {
                if (T88.TEST) {
                    LogUtils.getLogger().error("{}", e.getMessage());
                }
            }
            GL11C.glDepthMask(true);
            GL11C.glColorMask(true, true, true, true);

        }
    }

    public static int bindBuffer(ByteBuffer buffer, int id) {
        int bufferId = GL15C.glGenBuffers();
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, bufferId);
        GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, buffer, GL15C.GL_STREAM_DRAW);
        GL30C.glBindVertexArray(id);
        int formerSize = 0;

        GL33C.glEnableVertexAttribArray(INSTANCE_UV_INDEX);
        GL20C.glVertexAttribPointer(INSTANCE_UV_INDEX, 4, GL11C.GL_FLOAT, false, SIZE_INSTANCE_BYTES, formerSize);
        formerSize += 4 * SIZE_FLOAT_OR_INT_BYTES;
        GL33C.glVertexAttribDivisor(INSTANCE_UV_INDEX, 1);

        GL33C.glEnableVertexAttribArray(INSTANCE_COLOR_INDEX);
        GL20C.glVertexAttribPointer(INSTANCE_COLOR_INDEX, 4, GL11C.GL_FLOAT, false, SIZE_INSTANCE_BYTES, formerSize);
        formerSize += 4 * SIZE_FLOAT_OR_INT_BYTES;
        GL33C.glVertexAttribDivisor(INSTANCE_COLOR_INDEX, 1);

        GL33C.glEnableVertexAttribArray(INSTANCE_UV2_INDEX);
        GL30C.glVertexAttribIPointer(INSTANCE_UV2_INDEX, 2, GL11C.GL_INT, SIZE_INSTANCE_BYTES, formerSize);
        formerSize += 4 * SIZE_FLOAT_OR_INT_BYTES;
        GL33C.glVertexAttribDivisor(INSTANCE_UV2_INDEX, 1);

        for (int i = 0; i < 4; i++) {
            GL33C.glEnableVertexAttribArray(INSTANCE_MATRIX_INDEX + i);
            GL20C.glVertexAttribPointer(INSTANCE_MATRIX_INDEX + i, 4, GL11C.GL_FLOAT, false, SIZE_INSTANCE_BYTES, formerSize);
            formerSize += 4 * SIZE_FLOAT_OR_INT_BYTES;
            GL33C.glVertexAttribDivisor(INSTANCE_MATRIX_INDEX + i, 1);
        }

        return bufferId;
    }

    public static void end(ByteBuffer instanceMatrixBuffer, int instanceMatrixBufferId) {
        GL33C.glDisableVertexAttribArray(INSTANCE_UV_INDEX);
        GL33C.glDisableVertexAttribArray(INSTANCE_COLOR_INDEX);
        GL33C.glDisableVertexAttribArray(INSTANCE_UV2_INDEX);
        for (int i = 0; i < 4; i++) {
            GL33C.glDisableVertexAttribArray(INSTANCE_MATRIX_INDEX + i);
        }
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
        GL15C.glDeleteBuffers(instanceMatrixBufferId);
        GL30C.glBindVertexArray(0);
        MemoryUtil.memFree(instanceMatrixBuffer);
    }

    public static class SimpleBlockPos {
        private int x, y, z;

        public SimpleBlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public void set(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public SimpleBlockPos copy() {
            return new SimpleBlockPos(x, y, z);
        }

        @Override
        public int hashCode() {
            return (y + z * 31) * 37 + x;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (!(obj instanceof SimpleBlockPos pos)) {
                return false;
            } else {
                return this.x == pos.x && this.y == pos.y && this.z == pos.z;
            }
        }

        private static final int PACKED_X_LENGTH = 1 + Mth.log2(Mth.smallestEncompassingPowerOfTwo(30000000));
        private static final int PACKED_Z_LENGTH = PACKED_X_LENGTH;
        public static final int PACKED_Y_LENGTH = 64 - PACKED_X_LENGTH - PACKED_Z_LENGTH;
        private static final long PACKED_X_MASK = (1L << PACKED_X_LENGTH) - 1L;
        private static final long PACKED_Y_MASK = (1L << PACKED_Y_LENGTH) - 1L;
        private static final long PACKED_Z_MASK = (1L << PACKED_Z_LENGTH) - 1L;
        private static final int Y_OFFSET = 0;
        private static final int Z_OFFSET = PACKED_Y_LENGTH;
        private static final int X_OFFSET = PACKED_Y_LENGTH + PACKED_Z_LENGTH;

        public long asLong() {
            long i = 0L;
            i |= ((long)x & PACKED_X_MASK) << X_OFFSET;
            i |= ((long) y & PACKED_Y_MASK);
            return i | ((long)z & PACKED_Z_MASK) << Z_OFFSET;
        }
    }
}
