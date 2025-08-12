package org.mythtv.leanfront.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.metadata.MetadataOutput;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.text.TextRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;

import com.google.firebase.crashlytics.buildtools.reloc.com.google.errorprone.annotations.CanIgnoreReturnValue;

import java.util.ArrayList;

@UnstableApi
public class MyRenderersFactory extends DefaultRenderersFactory {

    private @ExtensionRendererMode int videoExtensionRendererMode;

    /**
     * @param context A {@link Context}.
     */
    @OptIn(markerClass = UnstableApi.class)
    public MyRenderersFactory(Context context) {
        super(context);
        videoExtensionRendererMode = EXTENSION_RENDERER_MODE_OFF;
    }

    /**
     * Sets the extension renderer mode, which determines if and how available extension renderers are
     * used. Note that extensions must be included in the application build for them to be considered
     * available.
     *
     * <p>The default value is {@link #EXTENSION_RENDERER_MODE_OFF}.
     *
     * @param videoExtensionRendererMode The extension renderer mode.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public final MyRenderersFactory setVideoExtensionRendererMode(
            @ExtensionRendererMode int videoExtensionRendererMode) {
        this.videoExtensionRendererMode = videoExtensionRendererMode;
        return this;
    }

    @Override
    public Renderer[] createRenderers(
            Handler eventHandler,
            VideoRendererEventListener videoRendererEventListener,
            AudioRendererEventListener audioRendererEventListener,
            TextOutput textRendererOutput,
            MetadataOutput metadataRendererOutput) {
        ArrayList<Renderer> renderersList = new ArrayList<>();
        buildVideoRenderers(
                context,
                videoExtensionRendererMode,
                mediaCodecSelector,
                enableDecoderFallback,
                eventHandler,
                videoRendererEventListener,
                allowedVideoJoiningTimeMs,
                renderersList);
        @Nullable
        AudioSink audioSink =
                buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams);
        if (audioSink != null) {
            buildAudioRenderers(
                    context,
                    extensionRendererMode,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    audioSink,
                    eventHandler,
                    audioRendererEventListener,
                    renderersList);
        }
        buildTextRenderers(
                context,
                textRendererOutput,
                eventHandler.getLooper(),
                extensionRendererMode,
                renderersList);
        buildMetadataRenderers(
                context,
                metadataRendererOutput,
                eventHandler.getLooper(),
                extensionRendererMode,
                renderersList);
        buildCameraMotionRenderers(context, extensionRendererMode, renderersList);
        buildImageRenderers(context, renderersList);
        buildMiscellaneousRenderers(context, eventHandler, extensionRendererMode, renderersList);
        return renderersList.toArray(new Renderer[0]);
    }

    protected void buildTextRenderers(
            Context context,
            TextOutput output,
            Looper outputLooper,
            @ExtensionRendererMode int extensionRendererMode,
            ArrayList<Renderer> out) {
        TextRenderer r = new TextRenderer(output, outputLooper);
        r.experimentalSetLegacyDecodingEnabled(true);
        out.add(r);
    }

}
