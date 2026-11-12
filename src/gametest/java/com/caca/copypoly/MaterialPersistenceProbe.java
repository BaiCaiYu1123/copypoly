package com.caca.copypoly;

import com.caca.copypoly.geometry.Polyhedron;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Opt-in, cross-process disk test. Run seed, then verify twice, using -PpersistencePhase. */
@PrefixGameTestTemplate(false)
public final class MaterialPersistenceProbe {
    private static final BlockPos START=new BlockPos(100008,64,100008);
    private static final Item[] MATERIALS={Items.STONE,Items.GLASS,Items.RED_STAINED_GLASS,Items.OAK_LEAVES,
        Items.GRASS_BLOCK,Items.OAK_LOG,com.simibubi.create.AllBlocks.ROSE_QUARTZ_TILES.asItem()};
    private static final String SHAPE="136,260,268,392,3171,3181,3491,3501,4104,4352,4368,4616,8196,8204,8320,8336,8576,8592,8708,8716,12296,12544,12560,12808,13411,13421,13731,13741,16520,16644,16652,16776";

    @GameTest(template="empty",templateNamespace="copypoly")
    public static void overlappingFillDiagnostic(GameTestHelper h) throws Exception {
        var p=h.absolutePos(new BlockPos(1,2,1));var q=p.offset(1,0,0);
        var source=h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withPermission(4).withSuppressedOutput();
        var dispatcher=h.getLevel().getServer().getCommands().getDispatcher();
        String a=p.getX()+" "+p.getY()+" "+p.getZ(), b=q.getX()+" "+q.getY()+" "+q.getZ();
        String block="copypoly:copycat_polyhedron{Material:{Name:\"minecraft:glass\"},Item:{id:\"minecraft:glass\",count:1}}";
        h.getLevel().setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());
        h.getLevel().setBlockAndUpdate(q,Blocks.AIR.defaultBlockState());
        dispatcher.execute("setblock "+a+" "+block,source);
        int changed=dispatcher.execute("fill "+a+" "+b+" "+block,source);
        var old=(PolyBlockEntity)h.getLevel().getBlockEntity(p);
        var added=(PolyBlockEntity)h.getLevel().getBlockEntity(q);
        com.mojang.logging.LogUtils.getLogger().info("CopyPoly overlap diagnostic: changed={}, existingMaterial={}, newMaterial={}",changed,old.getMaterial(),added.getMaterial());
        h.assertTrue(added.getMaterial().is(Blocks.GLASS),"Fresh fill lost material");
        // Resetting to air first is the safe command workflow, regardless of overlap behavior.
        dispatcher.execute("fill "+a+" "+b+" air",source);
        dispatcher.execute("fill "+a+" "+b+" "+block,source);
        for(var pos:BlockPos.betweenClosed(p,q)) {
            var be=(PolyBlockEntity)h.getLevel().getBlockEntity(pos);
            h.assertTrue(be.getMaterial().is(Blocks.GLASS) && be.getConsumedItem().is(Items.GLASS),"Air-reset fill lost material");
        }
        h.succeed();
    }

    @GameTest(template="empty",templateNamespace="copypoly",timeoutTicks=1200)
    public static void materialPersistsAcrossProcessRestarts(GameTestHelper h) throws Exception {
        var level=h.getLevel();var server=level.getServer();
        var source=server.createCommandSourceStack().withLevel(level).withPermission(4).withSuppressedOutput();
        var commands=server.getCommands().getDispatcher();
        for(int x=START.getX()>>4;x<=(START.getX()+48)>>4;x++)
            for(int z=START.getZ()>>4;z<=(START.getZ()+31)>>4;z++)level.getChunk(x,z);
        String phase=System.getProperty("copypoly.persistencePhase");
        if(phase.equals("seed")) {
            for(var pos:BlockPos.betweenClosed(START,START.offset(31,31,31)))
                level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
            int filled=commands.execute("fill 100008 64 100008 100039 95 100039 copypoly:copycat_polyhedron{Points:[I;"+SHAPE+"],Material:{Name:\"minecraft:glass\"},Item:{id:\"minecraft:glass\",count:1}}",source);
            h.assertTrue(filled==32768,"Fill did not generate the full volume");
            for(int mode=0;mode<2;mode++) {
                var player=h.makeMockPlayer(mode==0?GameType.SURVIVAL:GameType.CREATIVE);
                for(int i=0;i<MATERIALS.length;i++) {
                    var pos=START.offset(40+i,0,mode*2);
                    level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(pos,Copypoly.POLY.get().defaultBlockState());
                    var be=(PolyBlockEntity)level.getBlockEntity(pos);
                    be.setPoly(Polyhedron.of(Polyhedron.parse("0,0,0;16,0,0;0,16,0;0,0,16")));
                    var stack=new ItemStack(MATERIALS[i],2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                    player.setPos(Vec3.atCenterOf(pos));
                    Copypoly.POLY.get().useItemOn(stack,be.getBlockState(),level,pos,player,InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
                }
            }
        } else h.assertTrue(phase.equals("verify"),"Use seed or verify for persistencePhase");
        int checked=0;
        for(var pos:BlockPos.betweenClosed(START,START.offset(31,31,31))) {
            var entity=level.getBlockEntity(pos);
            h.assertTrue(entity instanceof PolyBlockEntity,"Missing command-created block at "+pos);
            var be=(PolyBlockEntity)entity;
            h.assertTrue(be.getMaterial().is(Blocks.GLASS) && be.getConsumedItem().is(Items.GLASS),"Lost command material at "+pos);
            h.assertTrue(be.poly().points().size()==32,"Lost command shape at "+pos);
            checked++;
        }
        for(int mode=0;mode<2;mode++)for(int i=0;i<MATERIALS.length;i++) {
            var be=(PolyBlockEntity)level.getBlockEntity(START.offset(40+i,0,mode*2));
            h.assertTrue(be!=null && be.getMaterial().is(((BlockItem)MATERIALS[i]).getBlock()),"Lost player-applied material: "+MATERIALS[i]);
            h.assertTrue(be.getConsumedItem().is(MATERIALS[i]) && be.poly().points().size()==4,"Lost material item or player shape");
            checked++;
        }
        server.saveAllChunks(false,true,false);
        com.mojang.logging.LogUtils.getLogger().info("CopyPoly persistence {}: {} block shapes and materials verified, then saved to disk",phase,checked);
        h.succeed();
    }
}
