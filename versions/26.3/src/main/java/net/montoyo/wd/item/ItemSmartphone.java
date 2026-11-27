package net.montoyo.wd.item;

public class ItemSmartphone extends ItemMinePad2 {

	public ItemSmartphone(Properties properties) {
		super(properties);
	}

	@Override
	public boolean isMobileLayout() {
		return true;
	}
}
