package zakyatto.easyrods.hold;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jspecify.annotations.Nullable;
import zakyatto.easyrods.EasyRods;
import zakyatto.easyrods.config.EasyRodsConfig;

/**
 * Creates, releases and cleans up rod holds on pressure plates.
 */
public final class PlateHolds {
	/** Radius 2 makes the plate's chunk entity-ticking and its 3x3 neighbourhood block-ticking. */
	private static final int RELEASE_TICKET_RADIUS = 2;
	/** Give up waiting for a release target chunk to load after this many ticks. */
	private static final int RELEASE_LOAD_TIMEOUT_TICKS = 20 * 60;

	private static final List<PendingRelease> PENDING_RELEASES = new ArrayList<>();

	private static EasyRodsConfig config = EasyRodsConfig.DEFAULT;
	private static @Nullable TicketType releaseTicket;

	private record PendingRelease(ResourceKey<Level> dimension, BlockPos pos, int deadlineTick) {
	}

	private PlateHolds() {
	}

	/**
	 * Applies the config, registers the release chunk ticket and hooks the Fabric events.
	 * Must be called once during mod initialization.
	 */
	public static void init(EasyRodsConfig loadedConfig) {
		config = loadedConfig;
		// Not persisted: a saved ticket of a modded type would break the dimension's ticket file
		// if the mod were removed. Plates still unpress via their own scheduled tick after a restart.
		releaseTicket = Registry.register(
			BuiltInRegistries.TICKET_TYPE,
			EasyRods.id("rod_release"),
			new TicketType(
				config.releaseChunkLoadTicks(),
				TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE
			)
		);

		UseItemCallback.EVENT.register(PlateHolds::onUseItem);
		ServerTickEvents.END_SERVER_TICK.register(PlateHolds::processPendingReleases);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING_RELEASES.clear());
	}

	/**
	 * Returns whether the plate at {@code pos} is held by a rod.
	 */
	public static boolean isHeld(Level level, BlockPos pos) {
		return level instanceof ServerLevel serverLevel && HeldPlates.get(serverLevel).isHeld(pos);
	}

	/**
	 * Called when a fishing hook touches a plate that a bobber can press. Makes the
	 * owner's rod hold the plate and removes the bobber, which the hold replaces.
	 */
	public static void onBobberTouch(ServerLevel level, BlockPos pos, FishingHook hook) {
		if (hook.isRemoved() || !(hook.getPlayerOwner() instanceof ServerPlayer player) || player.fishing != hook) {
			return;
		}

		HeldPlates plates = HeldPlates.get(level);
		if (plates.isHeld(pos)) {
			return;
		}

		InteractionHand hand = rodHand(player);
		if (hand == null) {
			return;
		}

		ItemStack rod = player.getItemInHand(hand);
		RodHold previous = RodHold.read(rod);
		if (previous != null) {
			if (!canPull(player, previous)) {
				return;
			}
			release(level.getServer(), previous);
		}

		BlockPos platePos = pos.immutable();
		RodHold hold = new RodHold(UUID.randomUUID(), GlobalPos.of(level.dimension(), platePos), player.getUUID());
		plates.put(platePos, hold.id());
		hold.writeTo(rod);
		hook.discard();
	}

	/**
	 * Called after a pressure plate block was removed or replaced by another block.
	 */
	public static void onPlateRemoved(ServerLevel level, BlockPos pos) {
		HeldPlates.get(level).remove(pos);
	}

	private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		ItemStack rod = player.getItemInHand(hand);
		if (!rod.is(Items.FISHING_ROD)) {
			return InteractionResult.PASS;
		}

		RodHold hold = RodHold.read(rod);
		if (hold == null) {
			return InteractionResult.PASS;
		}

		if (!canPull(serverPlayer, hold)) {
			serverPlayer.sendOverlayMessage(Component.literal("Only the player who cast this rod can pull it."));
			return InteractionResult.FAIL;
		}

		RodHold.clear(rod);
		boolean released = release(serverPlayer.level().getServer(), hold);

		level.playSound(
			null,
			player.getX(),
			player.getY(),
			player.getZ(),
			SoundEvents.FISHING_BOBBER_RETRIEVE,
			SoundSource.NEUTRAL,
			1.0F,
			0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F)
		);
		rod.causeUseVibration(player, GameEvent.ITEM_INTERACT_FINISH);

		if (released) {
			if (config.durabilityCostPerPull() > 0) {
				rod.hurtAndBreak(config.durabilityCostPerPull(), player, hand);
			}
		} else {
			serverPlayer.sendOverlayMessage(Component.literal("The pressure plate this rod held no longer exists."));
		}
		return InteractionResult.SUCCESS;
	}

	private static boolean canPull(ServerPlayer player, RodHold hold) {
		return !config.ownerOnlyPull() || hold.owner().equals(player.getUUID());
	}

	private static @Nullable InteractionHand rodHand(Player player) {
		if (player.getMainHandItem().is(Items.FISHING_ROD)) {
			return InteractionHand.MAIN_HAND;
		}
		if (player.getOffhandItem().is(Items.FISHING_ROD)) {
			return InteractionHand.OFF_HAND;
		}
		return null;
	}

	/**
	 * Removes {@code hold} from its plate and queues the plate to be re-checked once its
	 * chunk is loaded and ticking.
	 *
	 * @return {@code false} if the hold was no longer valid (plate broken, replaced or re-held)
	 */
	private static boolean release(MinecraftServer server, RodHold hold) {
		ServerLevel level = server.getLevel(hold.plate().dimension());
		if (level == null) {
			return false;
		}

		BlockPos pos = hold.plate().pos();
		if (!HeldPlates.get(level).remove(pos, hold.id())) {
			return false;
		}

		addReleaseTicket(level, pos);
		PENDING_RELEASES.add(new PendingRelease(level.dimension(), pos, server.getTickCount() + RELEASE_LOAD_TIMEOUT_TICKS));
		return true;
	}

	private static void processPendingReleases(MinecraftServer server) {
		if (!PENDING_RELEASES.isEmpty()) {
			int now = server.getTickCount();
			PENDING_RELEASES.removeIf(pending -> tryRelease(server, pending, now));
		}
	}

	/**
	 * @return whether {@code pending} is finished and can be dropped from the queue
	 */
	private static boolean tryRelease(MinecraftServer server, PendingRelease pending, int now) {
		ServerLevel level = server.getLevel(pending.dimension());
		if (level == null) {
			return true;
		}

		BlockPos pos = pending.pos();
		if (!level.isPositionTickingWithEntitiesLoaded(ChunkPos.pack(pos))) {
			if (now >= pending.deadlineTick()) {
				EasyRods.LOGGER.warn("Chunk of released plate at {} in {} did not load in time", pos, pending.dimension().identifier());
				return true;
			}
			return false;
		}

		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof BasePressurePlateBlock && !HeldPlates.get(level).isHeld(pos)) {
			// Runs vanilla's scheduled plate check now: it recounts entities and, if nothing is on
			// the plate, unpresses it with the normal redstone updates, sound and game event.
			state.tick(level, pos, level.getRandom());
		}

		// Re-adding resets the timeout, so the configured window starts at the actual release.
		addReleaseTicket(level, pos);
		return true;
	}

	private static void addReleaseTicket(ServerLevel level, BlockPos pos) {
		level.getChunkSource().addTicketWithRadius(releaseTicket, ChunkPos.containing(pos), RELEASE_TICKET_RADIUS);
	}
}
