package net.fasttotemoffhand.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a Totem of Undying behave like a shield when shift-clicked in the
 * player inventory: if the offhand slot is empty, the totem is quick-moved
 * straight into the offhand.
 *
 * <p>Vanilla only does this for items that carry an {@code Equippable}
 * component pointing at the offhand (e.g. shields). A totem has no such
 * component, so vanilla routes it through the regular inventory/hotbar move.
 * This mixin adds a totem-specific shortcut before the vanilla logic runs.</p>
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {

	// Slot layout of InventoryMenu:
	//   0        -> crafting result
	//   1 - 4    -> crafting grid
	//   5 - 8    -> armor
	//   9 - 35   -> main inventory
	//   36 - 44  -> hotbar
	//   45       -> offhand
	private static final int OFFHAND_SLOT = 45;
	private static final int FIRST_INVENTORY_SLOT = 9;

	@Shadow
	public abstract Slot getSlot(int index);

	@Shadow
	protected abstract boolean moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection);

	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
	private void fastTotemOffhand$moveTotemToOffhand(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		// Only react to shift-clicks coming from the main inventory or hotbar
		// (indices 9..44). This leaves the offhand slot itself and the
		// crafting/armor slots to the vanilla behaviour.
		if (index < FIRST_INVENTORY_SLOT || index >= OFFHAND_SLOT) {
			return;
		}

		Slot slot = this.getSlot(index);
		if (!slot.hasItem()) {
			return;
		}

		ItemStack stack = slot.getItem();
		if (!stack.is(Items.TOTEM_OF_UNDYING)) {
			return;
		}

		// The offhand must be free, mirroring vanilla's behaviour for shields.
		if (this.getSlot(OFFHAND_SLOT).hasItem()) {
			return;
		}

		ItemStack original = stack.copy();

		if (!this.moveItemStackTo(stack, OFFHAND_SLOT, OFFHAND_SLOT + 1, false)) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}

		if (stack.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		if (stack.getCount() == original.getCount()) {
			// Nothing actually moved.
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}

		slot.onTake(player, stack);
		cir.setReturnValue(original);
	}
}
