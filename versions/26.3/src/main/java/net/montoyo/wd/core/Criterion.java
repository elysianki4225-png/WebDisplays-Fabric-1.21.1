/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.advancements.triggers.CriterionTriggerInstance;
import net.minecraft.advancements.triggers.CriterionValidator;
import net.minecraft.resources.Identifier;
import net.minecraft.server.PlayerAdvancements;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class Criterion implements CriterionTrigger<Criterion.Instance> {

    /**
     * WebDisplays criteria never carry conditions, so the instance is a constant decoded
     * straight out of {"trigger":"webdisplays:x"} (same shape as vanilla's ImpossibleTrigger).
     */
    public static class Instance implements CriterionTriggerInstance {

        public static final Codec<Instance> CODEC = MapCodec.unit(new Instance()).codec();

        @Override
        public void validate(CriterionValidator validator) {
        }
    }

    private final Identifier id;
    private final HashMap<PlayerAdvancements, ArrayList<Listener<Instance>>> map = new HashMap<>();

    public Criterion(@NotNull String name) {
        id = Identifier.fromNamespaceAndPath("webdisplays", name);
    }

    @NotNull
    public Identifier getId() {
        return id;
    }

    @Override
    @NotNull
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    @Override
    public void addPlayerListener(PlayerAdvancements adv, Listener<Instance> l) {
        map.computeIfAbsent(adv, k -> new ArrayList<>()).add(l);
    }

    @Override
    public void removePlayerListener(PlayerAdvancements adv, Listener<Instance> l) {
        map.computeIfPresent(adv, (k, v) -> {
            v.remove(l);
            return v.isEmpty() ? null : v;
        });
    }

    @Override
    public void removePlayerListeners(PlayerAdvancements adv) {
        map.remove(adv);
    }

    public void trigger(PlayerAdvancements ply) {
        ArrayList<Listener<Instance>> listeners = map.get(ply);

        if(listeners != null) {
            Listener[] copy = listeners.toArray(new Listener[0]); //We need to make a copy, otherwise we get a ConcurrentModificationException
            Arrays.stream(copy).forEach(l -> l.run(ply));
        }
    }

}
