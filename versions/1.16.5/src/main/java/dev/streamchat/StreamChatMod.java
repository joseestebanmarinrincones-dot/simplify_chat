package dev.streamchat;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(StreamChatMod.MODID)
public final class StreamChatMod {
    public static final String MODID = "streamchat";
    public static final Logger LOGGER = LogManager.getLogger();

    public StreamChatMod() {
        ClientBootstrap.init();
    }
}
