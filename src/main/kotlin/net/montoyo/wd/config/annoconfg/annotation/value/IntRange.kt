package net.montoyo.wd.config.annoconfg.annotation.value

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class IntRange(val minV: Int, val maxV: Int)
