/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.mixin;

import com.chunkworks.serfdom.market.ClosedOffer;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A trade the villager's purse can't pay reads as sold out while a player trades (D-0006): the trade
 * screen draws its cross and its result slot stays empty. The flag is never saved, and the offer's
 * uses are never touched, so restocking and demand stay vanilla's. It goes with a copy: the offers
 * packet copies the offers before it writes them (and in single player hands the copy over
 * unwritten), so without it the client never saw a trade closed. */
@Mixin(MerchantOffer.class)
abstract class MerchantOfferMixin implements ClosedOffer {
    @Unique private boolean serfdom$closed;

    @Override public void serfdom$close(boolean closed) { serfdom$closed = closed; }
    @Override public boolean serfdom$closed() { return serfdom$closed; }

    @Inject(method = "<init>(Lnet/minecraft/world/item/trading/MerchantOffer;)V", at = @At("RETURN"))
    private void serfdom$copyClosed(MerchantOffer other, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        serfdom$closed = ((ClosedOffer) other).serfdom$closed();
    }

    @Inject(method = "isOutOfStock", at = @At("HEAD"), cancellable = true)
    private void serfdom$closedByThePurse(CallbackInfoReturnable<Boolean> cir) {
        if (serfdom$closed) cir.setReturnValue(true);
    }
}
