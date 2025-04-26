package dan200.computercraft.client.gui.widgets;

import dan200.computercraft.client.gui.widgets.Widget;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;

public class WidgetContainer extends Widget {
    private ArrayList<Widget> m_widgets;
    private Widget m_modalWidget;

    public WidgetContainer(int x, int y, int w, int h)
    {
        super(x, y, w, h);
    }
}
