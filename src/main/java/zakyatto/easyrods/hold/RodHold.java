package zakyatto.easyrods.hold;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;
import zakyatto.easyrods.EasyRods;

/**
 * The hold a fishing rod has on a pressure plate, stored in the rod's
 * {@code minecraft:custom_data} component so vanilla clients can carry it.
 *
 * @param id    unique per cast; must match the plate's entry in {@link HeldPlates} to be valid
 * @param plate dimension and position of the held plate
 * @param owner UUID of the player who cast the rod
 */
public record RodHold(UUID id, GlobalPos plate, UUID owner) {
	private static final String KEY = EasyRods.MOD_ID + ":hold";

	private static final Codec<RodHold> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		UUIDUtil.CODEC.fieldOf("id").forGetter(RodHold::id),
		GlobalPos.CODEC.fieldOf("plate").forGetter(RodHold::plate),
		UUIDUtil.CODEC.fieldOf("owner").forGetter(RodHold::owner)
	).apply(instance, RodHold::new));

	/**
	 * Returns the hold stored on {@code stack}, or {@code null} if it has none.
	 */
	public static @Nullable RodHold read(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? null : data.copyTag().read(KEY, CODEC).orElse(null);
	}

	/**
	 * Stores this hold on {@code stack}, replacing any previous hold.
	 */
	public void writeTo(ItemStack stack) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.store(KEY, CODEC, this));
	}

	/**
	 * Removes the hold from {@code stack}. The component itself is removed when
	 * nothing else is stored in it, so the rod matches a fresh one again.
	 */
	public static void clear(ItemStack stack) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(KEY));
	}
}
