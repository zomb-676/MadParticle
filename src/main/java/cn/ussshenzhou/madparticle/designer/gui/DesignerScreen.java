package cn.ussshenzhou.madparticle.designer.gui;

import cn.ussshenzhou.madparticle.designer.universal.screen.TScreen;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.TranslatableComponent;

/**
 * @author USS_Shenzhou
 */
public class DesignerScreen extends TScreen {

    public DesignerScreen() {
        super(new TranslatableComponent("gui.mp.designer.title"));
    }

    @Override
    protected void layout(int width, int height) {
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public void render(PoseStack pPoseStack, int pMouseX, int pMouseY, float pPartialTick) {
        super.render(pPoseStack, pMouseX, pMouseY, pPartialTick);
    }
}
