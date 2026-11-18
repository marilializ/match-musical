import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JPanel;

/** Distribuicao percentual de decisoes, recalculada com dados consultados no banco. */
public class GraficoPizzaPanel extends JPanel {
    private static final Color[] COLORS = {new Color(0x2a78d6), new Color(0xeb6834), new Color(0x8c63bb)};
    private final String titulo;
    private List<String> labels = List.of();
    private List<Integer> counts = List.of();
    public GraficoPizzaPanel() { this("Swipes por decisão"); }
    public GraficoPizzaPanel(String titulo) { this.titulo = titulo; setPreferredSize(new Dimension(400, 260)); setToolTipText(""); }
    public void setDados(List<String> labels, List<Integer> counts) {
        this.labels = List.copyOf(labels); this.counts = List.copyOf(counts); repaint();
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(getFont().deriveFont(java.awt.Font.BOLD, 14f));
        g.drawString(titulo, 16, 28);
        g.setFont(getFont());
        int total = counts.stream().mapToInt(Integer::intValue).sum();
        if (total == 0) { g.drawString("Sem swipes no período.", 16, 55); g.dispose(); return; }
        int x = 20, y = 48, d = Math.min(170, getHeight() - 65);
        int start = 0;
        for (int i = 0; i < counts.size(); i++) {
            int arc = i == counts.size() - 1 ? 360 - start : (int) Math.round(360.0 * counts.get(i) / total);
            g.setColor(COLORS[i % COLORS.length]);
            g.fillArc(x, y, d, d, start, arc);
            start += arc;
            int ly = 70 + i * 28;
            g.fillRect(x + d + 18, ly - 10, 12, 12);
            g.setColor(Color.DARK_GRAY);
            g.drawString(labels.get(i) + ": " + counts.get(i) + " (" + String.format("%.1f", 100.0 * counts.get(i) / total) + "%)", x + d + 36, ly);
        }
        g.dispose();
    }
    @Override public String getToolTipText(MouseEvent event) {
        int total = counts.stream().mapToInt(Integer::intValue).sum();
        if (total == 0) return null;
        int d = Math.min(170, getHeight() - 65);
        double dx = event.getX() - (20 + d / 2.0);
        double dy = (48 + d / 2.0) - event.getY();
        if (dx * dx + dy * dy > d * d / 4.0) return null;
        double angle = (Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360;
        double end = 0;
        for (int i = 0; i < counts.size(); i++) {
            end += 360.0 * counts.get(i) / total;
            if (angle < end || i == counts.size() - 1)
                return labels.get(i) + ": " + counts.get(i) + " ("
                        + String.format("%.1f", 100.0 * counts.get(i) / total) + "%)";
        }
        return null;
    }
}
