package xerca.xercamusic.common.tile_entity;

import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ObjectHolder;
import xerca.xercamusic.common.XercaMusic;
import xerca.xercamusic.common.block.Blocks;

import static xerca.xercamusic.common.XercaMusic.Null;

@ObjectHolder(XercaMusic.MODID)
public class TileEntities {
    public static final TileEntityType<?> METRONOME = Null();
    public static final TileEntityType<?> MUSIC_BOX = Null();

    @Mod.EventBusSubscriber(modid = XercaMusic.MODID, bus=Mod.EventBusSubscriber.Bus.MOD)
    public static class RegistrationHandler {
        @SubscribeEvent
        public static void registerTileEntities(final RegistryEvent.Register<TileEntityType<?>> event) {
            XercaMusic.LOGGER.info("XercaMusic: Registering tile entities");
            event.getRegistry().register(TileEntityType.Builder.create(TileEntityMetronome::new, Blocks.BLOCK_METRONOME).build(null).setRegistryName(XercaMusic.MODID, "metronome"));
            event.getRegistry().register(TileEntityType.Builder.create(TileEntityMusicBox::new, Blocks.MUSIC_BOX).build(null).setRegistryName(XercaMusic.MODID, "music_box"));
        }

    }
}
