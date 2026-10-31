package dev.streamchat;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Punto de entrada del mod. Todo el trabajo real vive en el lado cliente:
 * el mod es clientSideOnly, por lo que un servidor nunca lo carga.
 */
@Mod(StreamChatMod.MODID)
public final class StreamChatMod {
    public static final String MODID = "streamchat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StreamChatMod(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientBootstrap.init(context);
        } else {
            LOGGER.warn("StreamChat es un mod de cliente y no se inicializara en el servidor.");
        }
    }
}
