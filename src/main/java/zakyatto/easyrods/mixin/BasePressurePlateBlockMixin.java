package zakyatto.easyrods.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zakyatto.easyrods.hold.PlateHolds;

@Mixin(BasePressurePlateBlock.class)
abstract class BasePressurePlateBlockMixin {
	@Shadow
	@Final
	protected static AABB TOUCH_AABB;

	@Shadow
	@Final
	protected BlockSetType type;

	@Inject(method = "entityInside", at = @At("HEAD"))
	private void easyRods$holdOnBobberTouch(
		BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise, CallbackInfo ci
	) {
		if (entity instanceof FishingHook hook
			&& level instanceof ServerLevel serverLevel
			&& this.easyRods$isPressableByBobber()
			&& hook.getBoundingBox().intersects(TOUCH_AABB.move(pos))) {
			PlateHolds.onBobberTouch(serverLevel, pos, hook);
		}
	}

	@Inject(method = "affectNeighborsAfterRemoval", at = @At("HEAD"))
	private void easyRods$clearHoldOnRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston, CallbackInfo ci) {
		PlateHolds.onPlateRemoved(level, pos);
	}

	@Unique
	private boolean easyRods$isPressableByBobber() {
		Object self = this;
		return self instanceof WeightedPressurePlateBlock
			|| self instanceof PressurePlateBlock && this.type.pressurePlateSensitivity() == BlockSetType.PressurePlateSensitivity.EVERYTHING;
	}
}
