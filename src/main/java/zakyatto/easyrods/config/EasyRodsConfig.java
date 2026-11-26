package zakyatto.easyrods.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import zakyatto.easyrods.EasyRods;

/**
 * Server configuration, stored as JSON in the Fabric config directory.
 *
 * @param durabilityCostPerPull durability removed from a rod each time it releases a plate
 * @param ownerOnlyPull         whether only the player who cast a rod may pull it
 * @param releaseChunkLoadTicks how long the plate's chunk stays loaded after a release, in ticks
 */
public record EasyRodsConfig(int durabilityCostPerPull, boolean ownerOnlyPull, int releaseChunkLoadTicks) {
	public static final EasyRodsConfig DEFAULT = new EasyRodsConfig(1, false, 100);

	private static final Codec<EasyRodsConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		option("durabilityCostPerPull", Codec.intRange(0, 1024), DEFAULT.durabilityCostPerPull)
			.forGetter(EasyRodsConfig::durabilityCostPerPull),
		option("ownerOnlyPull", Codec.BOOL, DEFAULT.ownerOnlyPull)
			.forGetter(EasyRodsConfig::ownerOnlyPull),
		option("releaseChunkLoadTicks", Codec.intRange(20, 72000), DEFAULT.releaseChunkLoadTicks)
			.forGetter(EasyRodsConfig::releaseChunkLoadTicks)
	).apply(instance, EasyRodsConfig::new));

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/**
	 * Loads the config from {@code path}, creating it with defaults if missing.
	 * A valid file is rewritten so newly added options appear in it. An invalid
	 * file is left untouched and defaults are used.
	 */
	public static EasyRodsConfig load(Path path) {
		EasyRodsConfig config = DEFAULT;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				DataResult<EasyRodsConfig> result = CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader));
				if (result.isError()) {
					EasyRods.LOGGER.error("Invalid config {}: {}. Using defaults.", path, result.error().orElseThrow().message());
					return DEFAULT;
				}
				config = result.getOrThrow();
			} catch (IOException | JsonParseException e) {
				EasyRods.LOGGER.error("Could not read config {}. Using defaults.", path, e);
				return DEFAULT;
			}
		}

		save(path, config);
		return config;
	}

	// Missing keys fall back to the default when reading, but every key is always written.
	private static <T> MapCodec<T> option(String name, Codec<T> codec, T defaultValue) {
		return MapCodec.of(codec.fieldOf(name), codec.optionalFieldOf(name, defaultValue));
	}

	private static void save(Path path, EasyRodsConfig config) {
		JsonElement json = CODEC.encodeStart(JsonOps.INSTANCE, config).getOrThrow();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(json, writer);
			}
		} catch (IOException e) {
			EasyRods.LOGGER.error("Could not write config {}", path, e);
		}
	}
}
