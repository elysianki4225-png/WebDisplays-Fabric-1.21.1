package net.montoyo.wd.item

class ItemSmartphone(properties: Properties) : ItemMinePad2(properties) {
    override fun isMobileLayout(): Boolean = true
}
