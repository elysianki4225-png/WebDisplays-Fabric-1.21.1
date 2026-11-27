package net.montoyo.wd.config;

import net.montoyo.wd.config.annoconfg.annotation.format.ConfigSide;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.config.annoconfg.AnnoCFG;
import net.montoyo.wd.config.annoconfg.annotation.format.*;
import net.montoyo.wd.config.annoconfg.annotation.value.Default;
import net.montoyo.wd.config.annoconfg.annotation.value.DoubleRange;
import net.montoyo.wd.config.annoconfg.annotation.value.IntRange;

@Config(type = ConfigSide.CLIENT)
public class ClientConfig {
	@SuppressWarnings("unused")
	private static final AnnoCFG CFG = new AnnoCFG(ConfigSide.CLIENT, ClientConfig.class);

	public static void init() {
		// loads the class
	}

	@Name("load_distance")
	@Comment("How far (in blocks) you can be before a screen starts rendering")
	@Translation("config.webdisplays.load_distance")
	@DoubleRange(minV = 0, maxV = Double.MAX_VALUE)
	@Default(valueD = 30)
	public static double loadDistance = 30.0;

	@Name("unload_distance")
	@Comment("How far you can be before a screen stops rendering")
	@Translation("config.webdisplays.unload_distance")
	@DoubleRange(minV = 0, maxV = Double.MAX_VALUE)
	@Default(valueD = 32)
	public static double unloadDistance = 32.0;

	@Name("pad_resolution")
	@Comment({
			"The resolution that minePads should use",
			"Smaller values produce lower qualities, higher values produce higher qualities",
			"A good goto value for this would be the height of your monitor, in pixels"
	})
	@Translation("config.webdisplays.pad_res")
	@IntRange(minV = 120, maxV = 2160)
	@Default(valueI = 720)
	public static int padResolution = 720;

	@Name("side_pad")
	@Comment({
			"When this is true, the minePad is placed off to the side of the screen when held",
			"When this is false, the minePad is placed closer to the center of the screen"
	})
	@Translation("config.webdisplays.side_pad")
	@Default(valueBoolean = true)
	public static boolean sidePad = true;

	@Name("switch_buttons")
	@Comment("If the left and right buttons should be swapped when using a laser")
	@Translation("config.webdisplays.switch_buttons")
	@Default(valueBoolean = true)
	public static boolean switchButtons = true;

	@Comment("Rendering & Chromium performance optimizations")
	@CFGSegment("performance")
	public static class Performance {
		@Name("backface_culling")
		@Comment("Skip rendering & texture updates for screen faces pointing away from the camera")
		@Translation("config.webdisplays.backface_culling")
		@Default(valueBoolean = true)
		public static boolean backfaceCulling = true;

		@Name("pause_culled_screens")
		@Comment("Pause Chromium offscreen painting when a screen stays behind the camera or culled")
		@Translation("config.webdisplays.pause_culled_screens")
		@Default(valueBoolean = true)
		public static boolean pauseCulledScreens = true;

		@Name("max_browsers_per_frame")
		@Comment("Maximum number of new screen browsers created per frame to prevent teleport/join lag spikes")
		@Translation("config.webdisplays.max_browsers_per_frame")
		@IntRange(minV = 1, maxV = 16)
		@Default(valueI = 1)
		public static int maxBrowsersPerFrame = 1;

		@Name("adaptive_lod")
		@Comment("Dynamically adjust Chromium render resolution based on screen distance and size")
		@Translation("config.webdisplays.adaptive_lod")
		@Default(valueBoolean = true)
		public static boolean adaptiveLod = true;

		@Name("lod_cooldown_ms")
		@Comment("Minimum interval in milliseconds between adaptive resolution LOD updates")
		@Translation("config.webdisplays.lod_cooldown_ms")
		@IntRange(minV = 200, maxV = 5000)
		@Default(valueI = 750)
		public static int lodCooldownMs = 750;
	}

	@Comment({
			"AutoVolume makes audio fade off based on distance"
	})
	@CFGSegment("auto_volume")
	public static class AutoVolumeControl {
		@Name("enabled")
		@Comment("Whether or not auto volume should be enabled")
		@Translation("config.webdisplays.auto_vol")
		@Default(valueBoolean = true)
		public static boolean enableAutoVolume = true;

		@Name("youtube_volume")
		@Comment("How loud youtube should be by default")
		@Translation("config.webdisplays.yt_vol")
		@DoubleRange(minV = 0, maxV = 100)
		@Default(valueD = 100)
		public static double ytVolume = 100.0;

		@Name("dist0")
		@Comment("Distance after which you can't hear anything (in blocks)")
		@Translation("config.webdisplays.d0")
		@DoubleRange(minV = 0, maxV = Double.MAX_VALUE)
		@Default(valueD = 30)
		public static double dist0 = 30.0;

		@Name("dist100")
		@Comment("Distance after which the sound starts dropping (in blocks)")
		@Translation("config.webdisplays.d100")
		@DoubleRange(minV = 0, maxV = Double.MAX_VALUE)
		@Default(valueD = 10)
		public static double dist100 = 10.0;
	}

	@SuppressWarnings("unused")
	public static void postLoad() {
		if (unloadDistance < loadDistance + 2.0)
			unloadDistance = loadDistance + 2.0;

		if (AutoVolumeControl.dist0 < AutoVolumeControl.dist100 + 0.1)
			AutoVolumeControl.dist0 = AutoVolumeControl.dist100 + 0.1;

		WebDisplays.INSTANCE.padResY = padResolution;
		WebDisplays.INSTANCE.padResX = WebDisplays.INSTANCE.padResY * WebDisplays.PAD_RATIO;

		WebDisplays.INSTANCE.unloadDistance = unloadDistance;
		WebDisplays.INSTANCE.unloadDistance2 = unloadDistance * unloadDistance;
		WebDisplays.INSTANCE.loadDistance2 = loadDistance * loadDistance;

		WebDisplays.INSTANCE.enableBackfaceCulling = Performance.backfaceCulling;
		WebDisplays.INSTANCE.pauseCulledScreens = Performance.pauseCulledScreens;
		WebDisplays.INSTANCE.maxBrowsersPerFrame = Performance.maxBrowsersPerFrame;
		WebDisplays.INSTANCE.enableAdaptiveLod = Performance.adaptiveLod;
		WebDisplays.INSTANCE.lodCooldownMs = Performance.lodCooldownMs;

		WebDisplays.INSTANCE.ytVolume = (float) AutoVolumeControl.ytVolume;
		WebDisplays.INSTANCE.avDist100 = (float) AutoVolumeControl.dist100;
		WebDisplays.INSTANCE.avDist0 = (float) AutoVolumeControl.dist0;
	}
}
