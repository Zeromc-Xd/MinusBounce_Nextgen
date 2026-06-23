/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.injection.forge.mixins.client;

import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.MovementInputFromOptions;
import net.minusmc.minusbounce.MinusBounce;
import net.minusmc.minusbounce.event.MoveInputEvent;
import net.minusmc.minusbounce.utils.movement.MovementCorrection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MovementInputFromOptions.class)
public class MixinMovementInputFromOptions extends MixinMovementInput {

    @Shadow
    @Final
    private GameSettings gameSettings;

    /**
     * @author fmcpe
     * @reason MoveInputEvent
     */
    @Overwrite
    public void updatePlayerMoveState() {
        final float strafe = this.moveStrafe;
        final float forward = this.moveForward;

        this.moveStrafe = 0.0F;
        this.moveForward = 0.0F;

        if (this.gameSettings.keyBindForward.isKeyDown()) {
            ++this.moveForward;
        }

        if (this.gameSettings.keyBindBack.isKeyDown()) {
            --this.moveForward;
        }

        if (this.gameSettings.keyBindLeft.isKeyDown()) {
            ++this.moveStrafe;
        }

        if (this.gameSettings.keyBindRight.isKeyDown()) {
            --this.moveStrafe;
        }

        this.jump = this.gameSettings.keyBindJump.isKeyDown();
        this.sneak = this.gameSettings.keyBindSneak.isKeyDown();

        final MoveInputEvent event = new MoveInputEvent(
            this.moveForward,
            this.moveStrafe,
            forward,
            strafe,
            this.jump, 
            this.sneak, 
            0.3D
        );

        MinusBounce.eventManager.callEvent(event);
        MovementCorrection.correctInput(event);

        final double sneakMultiplier = event.getSneakMultiplier();
        this.moveForward = event.getForward();
        this.moveStrafe = event.getStrafe();
        this.jump = event.getJump();
        this.sneak = event.getSneak();
        
        if (this.sneak) {
            this.moveStrafe = (float) ((double) this.moveStrafe * sneakMultiplier);
            this.moveForward = (float) ((double) this.moveForward * sneakMultiplier);
        }
    }
}
