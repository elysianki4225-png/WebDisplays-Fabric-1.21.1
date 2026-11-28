package net.montoyo.wd.config.annoconfg.annotation.format

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Config(val type: ConfigSide, val path: String = "")
