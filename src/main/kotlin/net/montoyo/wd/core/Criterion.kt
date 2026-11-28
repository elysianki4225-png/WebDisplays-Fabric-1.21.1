/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.core

import com.mojang.serialization.Codec
import net.minecraft.advancements.CriterionTrigger
import net.minecraft.advancements.CriterionTrigger.Listener
import net.minecraft.advancements.CriterionTriggerInstance
import net.minecraft.advancements.critereon.CriterionValidator
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.PlayerAdvancements

class Criterion(name: String) : CriterionTrigger<Criterion.Instance> {
    /**
     * WebDisplays criteria never carry conditions, so the instance is a constant decoded
     * straight out of {"trigger":"webdisplays:x"} (same shape as vanilla's ImpossibleTrigger).
     */
    class Instance : CriterionTriggerInstance {
        override fun validate(validator: CriterionValidator) {
        }

        companion object {
            @JvmField
            val CODEC: Codec<Instance> = Codec.unit(Instance())
        }
    }

    @JvmField
    val id: ResourceLocation = ResourceLocation.fromNamespaceAndPath("webdisplays", name)
    private val map = HashMap<PlayerAdvancements, ArrayList<Listener<Instance>>>()

    fun getId(): ResourceLocation = id

    override fun codec(): Codec<Instance> = Instance.CODEC

    override fun addPlayerListener(adv: PlayerAdvancements, l: Listener<Instance>) {
        map.computeIfAbsent(adv) { ArrayList() }.add(l)
    }

    override fun removePlayerListener(adv: PlayerAdvancements, l: Listener<Instance>) {
        map.computeIfPresent(adv) { _, v ->
            v.remove(l)
            if (v.isEmpty()) null else v
        }
    }

    override fun removePlayerListeners(adv: PlayerAdvancements) {
        map.remove(adv)
    }

    fun trigger(ply: PlayerAdvancements) {
        val listeners = map[ply] ?: return
        val copy = listeners.toTypedArray() // Copy to avoid ConcurrentModificationException
        for (l in copy) {
            l.run(ply)
        }
    }
}
