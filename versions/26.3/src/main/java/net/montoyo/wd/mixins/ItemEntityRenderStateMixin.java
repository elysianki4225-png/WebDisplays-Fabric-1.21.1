package net.montoyo.wd.mixins;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.montoyo.wd.client.renderers.DroppedPadStateAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

@Mixin(ItemEntityRenderState.class)
public class ItemEntityRenderStateMixin implements DroppedPadStateAccess {
    @Unique
    private boolean wd$isPad;
    @Unique
    private boolean wd$isMobile;
    @Unique
    private UUID wd$padId;

    @Override
    public boolean wd$isPad() {
        return wd$isPad;
    }

    @Override
    public void wd$setIsPad(boolean value) {
        this.wd$isPad = value;
    }

    @Override
    public boolean wd$isMobile() {
        return wd$isMobile;
    }

    @Override
    public void wd$setIsMobile(boolean value) {
        this.wd$isMobile = value;
    }

    @Override
    public UUID wd$getPadId() {
        return wd$padId;
    }

    @Override
    public void wd$setPadId(UUID value) {
        this.wd$padId = value;
    }
}
