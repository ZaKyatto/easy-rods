package zakyatto.easyrods.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import zakyatto.easyrods.hold.PlateHolds;

@Mixin({PressurePlateBlock.class, WeightedPressurePlateBlock.class})
abstract class PressurePlateSignalMixin {
	// Owner-less target: each class invokes the inherited static getEntityCount on itself.
	@ModifyExpressionValue(
		method = "getSignalStrength",
		at = @At(value = "INVOKE", target = "getEntityCount(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/AABB;Ljava/lang/Class;)I")
	)
	private int easyRods$countHoldAsBobber(int entityCount, Level level, BlockPos pos) {
		return PlateHolds.isHeld(level, pos) ? entityCount + 1 : entityCount;
	}
}
