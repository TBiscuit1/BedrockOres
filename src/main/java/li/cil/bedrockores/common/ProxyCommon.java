package li.cil.bedrockores.common;

import li.cil.bedrockores.common.config.Settings;
import li.cil.bedrockores.common.config.VeinConfig;
import li.cil.bedrockores.common.init.Blocks;
import li.cil.bedrockores.common.init.Items;
import li.cil.bedrockores.common.network.Network;
import li.cil.bedrockores.common.sound.Sounds;
import li.cil.bedrockores.common.world.Retrogen;
import li.cil.bedrockores.common.world.WorldGeneratorBedrockOre;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/**
 * Takes care of common setup.
 */
@Mod.EventBusSubscriber
public class ProxyCommon {
    public void onPreInit(final FMLPreInitializationEvent event) {
    }

    public void onInit(final FMLInitializationEvent event) {
        Sounds.INSTANCE.init();
        Network.INSTANCE.init();

        GameRegistry.registerWorldGenerator(WorldGeneratorBedrockOre.INSTANCE, Settings.worldGeneratorWeight);

        if (Settings.retrogenSpeed > 0) {
            MinecraftForge.EVENT_BUS.register(Retrogen.INSTANCE);
        }
    }

    public void onPostInit(final FMLPostInitializationEvent event) {
        VeinConfig.INSTANCE.load();
    }

    // --------------------------------------------------------------------- //

    @SubscribeEvent
    public static void handleRegisterBlocksEvent(final RegistryEvent.Register<Block> event) {
        Blocks.register(event.getRegistry());
    }

    @SubscribeEvent
    public static void handleRegisterItemsEvent(final RegistryEvent.Register<Item> event) {
        Items.register(event.getRegistry());
    }
}
