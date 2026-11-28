package net.montoyo.wd.config.annoconfg.annotation.value

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class LongRange(val minV: Long, val maxV: Long)
