package org.mythtv.leanfront.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.ExperimentalApi;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.metadata.MetadataOutput;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.text.TextRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;

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
     */
    public final void setVideoExtensionRendererMode(
            @ExtensionRendererMode int videoExtensionRendererMode) {
        this.videoExtensionRendererMode = videoExtensionRendererMode;
    }

    @NonNull
    @Override
    public Renderer[] createRenderers(
            @NonNull Handler eventHandler,
            @NonNull VideoRendererEventListener videoRendererEventListener,
            @NonNull AudioRendererEventListener audioRendererEventListener,
            @NonNull TextOutput textRendererOutput,
            @NonNull MetadataOutput metadataRendererOutput) {
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
                buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParameters);
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

    @OptIn(markerClass = ExperimentalApi.class)
    protected void buildTextRenderers(
            @NonNull Context context,
            @NonNull TextOutput output,
            @NonNull Looper outputLooper,
            @ExtensionRendererMode int extensionRendererMode,
            ArrayList<Renderer> out) {
        TextRenderer r = new TextRenderer(output, outputLooper);
        //noinspection deprecation
        r.experimentalSetLegacyDecodingEnabled(true);
        out.add(r);
    }

}
