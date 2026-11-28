package net.montoyo.wd.net.server_bound

import net.minecraft.core.component.DataComponents
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.montoyo.wd.item.ItemMinePad2
import net.montoyo.wd.net.Packet
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.utilities.NbtCompat
import java.util.UUID

class C2SMessageMinepadUrl : Packet {
    @JvmField
    internal var id: UUID

    @JvmField
    internal var url: String

    constructor(id: UUID, url: String) {
        this.id = id
        this.url = url
    }

    constructor(buf: FriendlyByteBuf) : super(buf) {
        this.id = buf.readUUID()
        this.url = buf.readUtf()
    }

    override fun write(buf: FriendlyByteBuf) {
        buf.writeUUID(id)
        buf.writeUtf(url)
    }

    protected fun merge(stack: ItemStack) {
        val tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        if (url == "") {
            tag.remove("PadID")
            tag.remove("PadIDMost")
            tag.remove("PadIDLeast")
        } else {
            NbtCompat.putUUID(tag, "PadID", id)
            tag.putString("PadURL", url)
            val padItem = stack.item
            if (padItem is ItemMinePad2 && padItem.isMobileLayout()) {
                tag.putBoolean("PadMobile", true)
            }
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }

    override fun handle(ctx: WDContext) {
        val sender = ctx.sender ?: return

        // check if the player is holding a minePad with the requested id
        for (value in InteractionHand.values()) {
            val stack = sender.getItemInHand(value)
            if (stack.item is ItemMinePad2) {
                val tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                if (NbtCompat.hasUUID(tag, "PadID")) {
                    val padId = NbtCompat.getUUID(tag, "PadID")
                    if (id == padId) {
                        merge(stack)
                        return
                    }
                }
            }
        }

        // also check the full inventory for a matching PadID
        val inv = sender.inventory
        for (i in 0 until inv.containerSize) {
            val stack = inv.getItem(i)
            if (stack.item is ItemMinePad2) {
                val tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                if (NbtCompat.hasUUID(tag, "PadID")) {
                    val padId = NbtCompat.getUUID(tag, "PadID")
                    if (id == padId) {
                        merge(stack)
                        return
                    }
                }
            }
        }

        // if the player is not holding the requested minePad, update the first one without an ID
        for (value in InteractionHand.values()) {
            val stack = sender.getItemInHand(value)
            if (stack.item is ItemMinePad2) {
                val tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                if (!NbtCompat.hasUUID(tag, "PadID")) {
                    merge(stack)
                    return
                }
            }
        }
    }
}
