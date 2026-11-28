package net.montoyo.wd.config.annoconfg.annotation.value

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Default(
    val valueB: Byte = 0,
    val valueS: Short = 0,
    val valueI: Int = 0,
    val valueL: Long = 0L,
    val valueF: Float = 0.0f,
    val valueD: Double = 0.0,
    val valueBoolean: Boolean = false,
    val valueStr: String = ""
)
