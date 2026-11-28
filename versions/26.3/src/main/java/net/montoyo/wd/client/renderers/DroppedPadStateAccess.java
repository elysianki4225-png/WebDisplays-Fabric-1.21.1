package net.montoyo.wd.client.renderers;

import java.util.UUID;

public interface DroppedPadStateAccess {
    boolean wd$isPad();
    void wd$setIsPad(boolean value);

    boolean wd$isMobile();
    void wd$setIsMobile(boolean value);

    UUID wd$getPadId();
    void wd$setPadId(UUID value);
}
