package com.caca.copypoly;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.*;

@Mod(Copypoly.MODID)
public final class Copypoly {
    public static final String MODID = "copypoly";
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredBlock<PolyBlock> POLY = BLOCKS.register("copycat_polyhedron", () -> new PolyBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2).sound(SoundType.METAL).noOcclusion().dynamicShape()));
    public static final DeferredItem<BlockItem> ITEM = ITEMS.register("copycat_polyhedron", () -> new PolyItem(POLY.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PolyBlockEntity>> ENTITY = ENTITIES.register(
        "copycat_polyhedron", () -> BlockEntityType.Builder.of(PolyBlockEntity::new, POLY.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<PolyMenu>> MENU = MENUS.register("polyhedron_editor",
        () -> IMenuTypeExtension.create(PolyMenu::new));

    public Copypoly(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus);
        bus.addListener(PolyPackets::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, Copypoly::rightClick);
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) e.accept(ITEM);
        });
    }
    static void rightClick(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
        if (e.isCanceled() || e.getUseBlock() == net.neoforged.neoforge.common.util.TriState.FALSE) return;
        var player=e.getEntity();
        if (!player.isShiftKeyDown() || e.getItemStack().is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH)) return;
        // Secondary use normally skips Block.useItemOn when holding items; intercept it before that skip.
        if (e.getLevel().getBlockState(e.getPos()).getBlock() instanceof PolyBlock block) {
            block.openEditor(e.getLevel(),e.getPos(),player);
            e.setCanceled(true);
            e.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(e.getLevel().isClientSide));
        }
    }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MODID, path); }
}
