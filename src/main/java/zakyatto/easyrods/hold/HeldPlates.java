package zakyatto.easyrods.hold;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import zakyatto.easyrods.EasyRods;

/**
 * Per-dimension saved data listing every held pressure plate and the ID of the
 * hold on it. Saved in the dimension's {@code data} folder.
 */
public final class HeldPlates extends SavedData {
	private record Entry(BlockPos pos, UUID id) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
			UUIDUtil.CODEC.fieldOf("id").forGetter(Entry::id)
		).apply(instance, Entry::new));
	}

	private static final Codec<HeldPlates> CODEC = Entry.CODEC.listOf()
		.fieldOf("holds")
		.codec()
		.xmap(HeldPlates::fromEntries, HeldPlates::toEntries);

	// Null data fixer type: there is no vanilla schema for this data. Fabric API skips fixing for null.
	private static final SavedDataType<HeldPlates> TYPE =
		new SavedDataType<>(EasyRods.id("held_plates"), HeldPlates::new, CODEC, null);

	private final Long2ObjectMap<UUID> holds = new Long2ObjectOpenHashMap<>();

	/**
	 * Returns the held plates of {@code level}, loading them from disk on first access.
	 */
	public static HeldPlates get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean isHeld(BlockPos pos) {
		return !this.holds.isEmpty() && this.holds.containsKey(pos.asLong());
	}

	public void put(BlockPos pos, UUID id) {
		this.holds.put(pos.asLong(), id);
		this.setDirty();
	}

	/**
	 * Removes the hold at {@code pos} only if it has the given ID.
	 *
	 * @return whether a matching hold was removed
	 */
	public boolean remove(BlockPos pos, UUID id) {
		boolean removed = this.holds.remove(pos.asLong(), id);
		if (removed) {
			this.setDirty();
		}
		return removed;
	}

	/**
	 * Removes whatever hold is at {@code pos}.
	 */
	public void remove(BlockPos pos) {
		if (!this.holds.isEmpty() && this.holds.remove(pos.asLong()) != null) {
			this.setDirty();
		}
	}

	private static HeldPlates fromEntries(List<Entry> entries) {
		HeldPlates plates = new HeldPlates();
		for (Entry entry : entries) {
			plates.holds.put(entry.pos().asLong(), entry.id());
		}
		return plates;
	}

	private List<Entry> toEntries() {
		List<Entry> entries = new ArrayList<>(this.holds.size());
		for (Long2ObjectMap.Entry<UUID> entry : this.holds.long2ObjectEntrySet()) {
			entries.add(new Entry(BlockPos.of(entry.getLongKey()), entry.getValue()));
		}
		return entries;
	}
}
