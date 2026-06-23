/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.injection.forge.mixins.patcher.bugfixes;

import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import paulscode.sound.SoundSystem;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mixin(SoundManager.class)
public abstract class MixinSoundManager {
    @Shadow public abstract boolean isSoundPlaying(ISound sound);

    @Shadow @Final private Map<String, ISound> playingSounds;

    private final List<String> p_pausedSounds = new ArrayList<>();

    @Unique private boolean p_updatingSounds;

    @Unique private final Queue<Runnable> p_deferredSoundActions = new ConcurrentLinkedQueue<>();

    @Inject(method = "updateAllSounds", at = @At("HEAD"))
    private void p_beginSoundUpdate(CallbackInfo ci) {
        p_updatingSounds = true;
    }

    @Inject(method = "updateAllSounds", at = @At("RETURN"))
    private void p_endSoundUpdate(CallbackInfo ci) {
        p_updatingSounds = false;

        Runnable action;
        while ((action = p_deferredSoundActions.poll()) != null) {
            action.run();
        }
    }

    @Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
    private void p_deferPlaySound(ISound sound, CallbackInfo ci) {
        if (!p_updatingSounds) {
            return;
        }

        p_deferredSoundActions.offer(() -> ((SoundManager) (Object) this).playSound(sound));
        ci.cancel();
    }

    @Inject(method = "playDelayedSound", at = @At("HEAD"), cancellable = true)
    private void p_deferDelayedSound(ISound sound, int delay, CallbackInfo ci) {
        if (!p_updatingSounds) {
            return;
        }

        p_deferredSoundActions.offer(() -> ((SoundManager) (Object) this).playDelayedSound(sound, delay));
        ci.cancel();
    }

    @Inject(method = "stopSound", at = @At("HEAD"), cancellable = true)
    private void p_deferStopSound(ISound sound, CallbackInfo ci) {
        if (!p_updatingSounds) {
            return;
        }

        p_deferredSoundActions.offer(() -> ((SoundManager) (Object) this).stopSound(sound));
        ci.cancel();
    }

    @Inject(method = "stopAllSounds", at = @At("HEAD"), cancellable = true)
    private void p_deferStopAllSounds(CallbackInfo ci) {
        if (!p_updatingSounds) {
            return;
        }

        p_deferredSoundActions.clear();
        p_deferredSoundActions.offer(() -> ((SoundManager) (Object) this).stopAllSounds());
        ci.cancel();
    }

    @SuppressWarnings("InvalidInjectorMethodSignature")
    @Redirect(
        method = "pauseAllSounds",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/audio/SoundManager$SoundSystemStarterThread;pause(Ljava/lang/String;)V", remap = false)
    )
    private void p_onlyPauseSoundIfNecessary(@Coerce SoundSystem soundSystem, String sound) {
        if (isSoundPlaying(playingSounds.get(sound))) {
            soundSystem.pause(sound);
            p_pausedSounds.add(sound);
        }
    }

    @Redirect(
        method = "resumeAllSounds",
        at = @At(value = "INVOKE", target = "Ljava/util/Set;iterator()Ljava/util/Iterator;", remap = false)
    )
    private Iterator<String> p_iterateOverPausedSounds(Set<String> keySet) {
        return p_pausedSounds.iterator();
    }

    @Redirect(
        method = "playSound",
        slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=Unable to play unknown soundEvent: {}", ordinal = 0)),
        at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;warn(Lorg/apache/logging/log4j/Marker;Ljava/lang/String;[Ljava/lang/Object;)V", ordinal = 0, remap = false)
    )
    private void p_silenceWarning(Logger instance, Marker marker, String s, Object[] objects) {
        // No-op
    }

    @Inject(method = "resumeAllSounds", at = @At("TAIL"))
    private void p_clearPausedSounds(CallbackInfo ci) {
        p_pausedSounds.clear();
    }
}
