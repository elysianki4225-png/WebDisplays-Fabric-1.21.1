package net.montoyo.wd.config.annoconfg.annotation.format

@Target(AnnotationTarget.FIELD, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Translation(val value: String)
