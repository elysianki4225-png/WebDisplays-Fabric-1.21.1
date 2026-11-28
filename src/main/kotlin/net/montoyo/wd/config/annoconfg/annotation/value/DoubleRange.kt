package net.montoyo.wd.config.annoconfg.annotation.value

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class DoubleRange(val minV: Double, val maxV: Double)
