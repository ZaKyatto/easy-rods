package zakyatto.easyrods;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zakyatto.easyrods.config.EasyRodsConfig;
import zakyatto.easyrods.hold.PlateHolds;

/**
 * Mod entrypoint for Easy Rods.
 */
public final class EasyRods implements ModInitializer {
	public static final String MOD_ID = "easy-rods";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		EasyRodsConfig config = EasyRodsConfig.load(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json"));
		PlateHolds.init(config);
	}

	/**
	 * Creates an identifier in this mod's namespace.
	 */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
