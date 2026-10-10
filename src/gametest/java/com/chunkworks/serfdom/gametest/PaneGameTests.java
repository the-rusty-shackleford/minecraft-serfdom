/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.gametest;

import com.chunkworks.serfdom.Serfdom;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Serfdom's own creative tab (Rusty, 2026-10-10: a pane for every mod with recipes): the Work Post,
 * the For Sale block and the Chain Lead, each in the vanilla tab it was in before too.
 */
@GameTestHolder("serfdom") @PrefixGameTestTemplate(false)
public final class PaneGameTests {

    @GameTest(template = "yard", timeoutTicks = 20)
    public void serfdomsTabListsItsBlocksAndTheChainLeadWhichStayInTheirVanillaTabs(GameTestHelper helper) {
        CreativeModeTab.ItemDisplayParameters params = new CreativeModeTab.ItemDisplayParameters(
                helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess());
        CreativeModeTab ours = Serfdom.CREATIVE_TAB.get();
        ours.buildContents(params);
        List<Item> items = ours.getDisplayItems().stream().map(ItemStack::getItem).toList();
        helper.assertValueEqual(items, List.of(Serfdom.WORK_POST_ITEM.get(), Serfdom.FOR_SALE_ITEM.get(), Serfdom.CHAIN_LEAD.get()), "Serfdom's tab");
        CreativeModeTab blocks = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(CreativeModeTabs.FUNCTIONAL_BLOCKS);
        blocks.buildContents(params);
        helper.assertTrue(blocks.getDisplayItems().stream().anyMatch(s -> s.is(Serfdom.WORK_POST_ITEM.get())), "the Work Post still in Functional Blocks");
        helper.succeed();
    }
}
