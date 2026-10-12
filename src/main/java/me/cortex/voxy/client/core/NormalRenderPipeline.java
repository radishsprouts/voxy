package me.cortex.voxy.client.core;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.gl.GlFramebuffer;
import me.cortex.voxy.client.core.gl.GlTexture;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager;
import me.cortex.voxy.client.core.rendering.hierachical.HierarchicalOcclusionTraverser;
import me.cortex.voxy.client.core.rendering.hierachical.NodeCleaner;
import me.cortex.voxy.client.core.rendering.post.FullscreenBlit;
import me.cortex.voxy.client.core.util.GPUTiming;
import org.joml.Matrix4f;

import java.util.List;
import java.util.function.BooleanSupplier;

import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL43.GL_DEPTH_STENCIL_TEXTURE_MODE;
import static org.lwjgl.opengl.GL45C.glBindTextureUnit;
import static org.lwjgl.opengl.GL45C.glTextureParameterf;

public class NormalRenderPipeline extends AbstractRenderPipeline {
    private GlTexture colourTex;
    private GlTexture colourSSAOTex;
    private final GlFramebuffer fbSSAO = new GlFramebuffer();

    private final boolean useEnvFog;
    private final FullscreenBlit finalBlit;

    private final SSAO ssao;

    protected NormalRenderPipeline(RenderProperties properties, AsyncNodeManager nodeManager, NodeCleaner nodeCleaner, HierarchicalOcclusionTraverser traversal, BooleanSupplier frexSupplier) {
        super(properties, nodeManager, nodeCleaner, traversal, frexSupplier, false);
        this.useEnvFog = VoxyConfig.CONFIG.useEnvironmentalFog;
        this.finalBlit = new FullscreenBlit(properties, "voxy:post/blit_texture_depth_cutout.frag",
                a->a.defineIf("USE_ENV_FOG", this.useEnvFog).define("EMIT_COLOUR"));


        this.ssao = SSAO.createSSAO(properties, VoxyConfig.CONFIG.getSSAOMode());
    }

    @Override
    protected int setup(Viewport<?> viewport, int sourceDepthTex, int srcWidth, int srcHeight) {
        if (this.colourTex == null || this.colourTex.getHeight() != viewport.height || this.colourTex.getWidth() != viewport.width) {
            if (this.colourTex != null) {
                this.colourTex.free();
                this.colourSSAOTex.free();
            }
            this.fb.resize(viewport.width, viewport.height);

            this.colourTex = new GlTexture().store(GL_RGBA8, 1, viewport.width, viewport.height);
            this.colourSSAOTex = new GlTexture().store(GL_RGBA8, 1, viewport.width, viewport.height);

            this.fb.framebuffer.bind(GL_COLOR_ATTACHMENT0, this.colourTex).verify();
            this.fbSSAO.bind(this.fb.getDepthAttachmentType(), this.fb.getDepthTex()).bind(GL_COLOR_ATTACHMENT0, this.colourSSAOTex).verify();


            glTextureParameterf(this.colourTex.id, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTextureParameterf(this.colourTex.id, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTextureParameterf(this.colourSSAOTex.id, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTextureParameterf(this.colourSSAOTex.id, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTextureParameterf(this.fb.getDepthTex().id, GL_DEPTH_STENCIL_TEXTURE_MODE, GL_DEPTH_COMPONENT);
        }

        this.initDepthStencil(sourceDepthTex, this.fb.framebuffer.id, srcWidth, srcHeight, viewport.width, viewport.height);
        return this.fb.getDepthTex().id;
    }

    @Override
    protected void postOpaquePreTranslucent(Viewport<?> viewport, int sourceDepthTexture) {
        GPUTiming.INSTANCE.marker("ao");
        this.ssao.computeSSAO(viewport, this.colourSSAOTex, this.colourTex, this.fb.getDepthTex(), sourceDepthTexture);
        glBindFramebuffer(GL_FRAMEBUFFER, this.fbSSAO.id);
    }

    @Override
    protected void finish(Viewport<?> viewport, int sourceDepthTexture, int outputFramebuffer, int srcWidth, int srcHeight) {
        this.finalBlit.bind();
        var mode = VoxyConfig.CONFIG.getFogMode();

        if (this.useEnvFog && mode != me.cortex.voxy.client.config.FogMode.OFF) {
            float start = viewport.fogParameters.environmentalStart();
            float end = viewport.fogParameters.environmentalEnd();

            if (mode == me.cortex.voxy.client.config.FogMode.DEFAULT) {
                // Voxy 原仕様のフォグ計算（そのままの濃いフォグ）
                if (Math.abs(end - start) > 1) {
                    float invEndFogDelta = 1f / (end - start);
                    float endDistance = Math.max(VoxyRenderSystem.getRenderDistance(), 20 * 16);
                    endDistance *= (float) Math.sqrt(3);
                    float startDelta = -start * invEndFogDelta;
                    glUniform4f(4, invEndFogDelta, startDelta, Math.clamp(endDistance * invEndFogDelta + startDelta, 0, 1), 0);
                    glUniform4f(5, viewport.fogParameters.red(), viewport.fogParameters.green(), viewport.fogParameters.blue(), viewport.fogParameters.alpha());
                } else {
                    glUniform4f(4, 0, 0, 0, 0);
                    glUniform4f(5, 0, 0, 0, 0);
                }
            } else {
                // 推奨モード: modの最初の描画地点（バニラ描画距離の端）から開始し、自然な視野距離でフェード
                float startOfVoxy = VoxyRenderSystem.getRenderDistance(); // バニラ描画距離（ブロック単位、例: 192m）
                float fogStart = startOfVoxy;
                float fogEnd;
                float maxOpacity = mode.maxOpacity;

                if (mode == me.cortex.voxy.client.config.FogMode.SUBTLE) {
                    // 自然な薄霧: 遠景の山並みを美しく透かしつつ大気感を付与
                    fogEnd = Math.max(startOfVoxy * 8.0f, 2048.0f);
                    maxOpacity = 0.45f;
                } else if (mode == me.cortex.voxy.client.config.FogMode.EXTENDED) {
                    // 遠景フェード: 1万ブロック級の広大な視界でも遠景を雄大にフェード
                    fogEnd = Math.max(startOfVoxy * 6.0f, 1536.0f);
                    maxOpacity = 0.65f;
                } else { // ATMOSPHERIC
                    // 大気霞: 晴天時の青みと澄んだ空気遠近感
                    fogEnd = Math.max(startOfVoxy * 10.0f, 3072.0f);
                    maxOpacity = 0.30f;
                }

                if (fogEnd <= fogStart) {
                    fogEnd = fogStart + 256.0f;
                }

                float invDelta = maxOpacity / (fogEnd - fogStart);
                float startDelta = -fogStart * invDelta;

                glUniform4f(4, invDelta, startDelta, maxOpacity, 0);
                glUniform4f(5, viewport.fogParameters.red(), viewport.fogParameters.green(), viewport.fogParameters.blue(), viewport.fogParameters.alpha());
            }
        } else {
            glUniform4f(4, 0, 0, 0, 0);
            glUniform4f(5, 0, 0, 0, 0);
        }

        glBindTextureUnit(3, this.colourSSAOTex.id);

        //Do alpha blending
        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        AbstractRenderPipeline.transformBlitDepth(this.finalBlit, this.fb.getDepthTex().id, outputFramebuffer, viewport, new Matrix4f(viewport.vanillaProjection).mul(viewport.modelView));
        glDisable(GL_BLEND);
    }

    @Override
    public void setupAndBindOpaque(Viewport<?> viewport) {
        this.fb.bind();
    }

    @Override
    public void setupAndBindTranslucent(Viewport<?> viewport) {
        glBindFramebuffer(GL_FRAMEBUFFER, this.fbSSAO.id);
    }

    @Override
    public void free() {
        this.finalBlit.delete();
        this.ssao.free();
        this.fbSSAO.free();
        if (this.colourTex != null) {
            this.colourTex.free();
            this.colourSSAOTex.free();
        }
        super.free0();
    }

    @Override
    public void addDebug(List<String> debug) {
        super.addDebug(debug);
        this.ssao.addDebugInfo(debug);
    }
}
