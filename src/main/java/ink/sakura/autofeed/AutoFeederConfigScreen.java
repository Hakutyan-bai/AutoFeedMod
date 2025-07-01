package ink.sakura.autofeed;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class AutoFeederConfigScreen extends Screen {
    private final AutoFeeder feeder;
    private SliderWidget intervalSlider;
    private SliderWidget rangeSlider;

    public AutoFeederConfigScreen(AutoFeeder feeder) {
        super(Text.literal("自动喂食设置"));
        this.feeder = feeder;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 3;

        // 移除TextWidget，标题将在render方法中绘制

        intervalSlider = new SliderWidget(centerX - 100, y, 200, 20, Text.literal("自动喂食的频率: " + feeder.getFeedIntervalTicks() + " tick"), (feeder.getFeedIntervalTicks() - 1) / 19.0) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("自动喂食的频率: " + (int)(1 + this.value * 19) + " tick"));
            }
            @Override
            protected void applyValue() {
                feeder.setFeedIntervalTicks((int)(1 + this.value * 19));
            }
        };
        this.addDrawableChild(intervalSlider);

        y += 30;
        rangeSlider = new SliderWidget(centerX - 100, y, 200, 20, Text.literal("自动喂食的范围: " + feeder.getFeedRange()), (feeder.getFeedRange() - 1) / 19.0) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("自动喂食的范围: " + (int)(1 + this.value * 19)));
            }
            @Override
            protected void applyValue() {
                feeder.setFeedRange((int)(1 + this.value * 19));
            }
        };
        this.addDrawableChild(rangeSlider);

        y += 40;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), btn -> this.close()).position(centerX - 50, y).size(100, 20).build());
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        // 手动绘制标题，兼容所有MC版本
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
    }
}