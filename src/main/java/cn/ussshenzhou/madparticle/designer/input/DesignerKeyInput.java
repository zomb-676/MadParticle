package cn.ussshenzhou.madparticle.designer.input;

import cn.ussshenzhou.madparticle.designer.gui.DesignerScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * @author USS_Shenzhou
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class DesignerKeyInput {
    public static final KeyMapping CALL_OUT_DESIGNER = new KeyMapping(
            "key.madparticle.designer", KeyConflictContext.UNIVERSAL, KeyModifier.ALT,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M,"key.categories.madparticle"
            );

    @SubscribeEvent
    public static void onKeyInput(InputEvent.KeyInputEvent event){
        if (CALL_OUT_DESIGNER.consumeClick()){
            Minecraft.getInstance().setScreen(new DesignerScreen());
        }
    }
}
