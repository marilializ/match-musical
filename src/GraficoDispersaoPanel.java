import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Dispersao: um ponto por musico (x = quantos instrumentos toca, y = media de
 * anos de experiencia). Como x e inteiro, os pontos de mesmo x ganham um
 * pequeno deslocamento fixo para nao se esconderem uns atras dos outros.
 * A reta tracejada e a tendencia (minimos quadrados); a dica mostra o musico.
 */
public class GraficoDispersaoPanel extends GraficoBase {
    private static final double DESLOCAMENTO_MAX = 0.18; // em unidades de x

    private final String eixoX;
    private final String eixoY;
    private List<DashboardDAO.PontoMusico> pontos = List.of();

    public GraficoDispersaoPanel(String titulo, String eixoX, String eixoY) {
        super(titulo);
        this.eixoX = eixoX;
        this.eixoY = eixoY;
    }

    public void setDados(List<DashboardDAO.PontoMusico> pontos) {
        this.pontos = List.copyOf(pontos);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = preparar(graphics);
        g.setFont(fonte(Font.PLAIN, 11f));
        g.setColor(TEXTO_SECUNDARIO);
        g.drawString("y: " + eixoY, MARGEM, MARGEM + 34);
        int topo = MARGEM + 48;
        if (pontos.isEmpty()) {
            semDados(g, "Nenhum músico com esse filtro.", topo + 12);
            g.dispose();
            return;
        }

        int maxX = 1;
        double maxY = 0;
        for (DashboardDAO.PontoMusico p : pontos) {
            maxX = Math.max(maxX, p.instrumentos());
            maxY = Math.max(maxY, p.mediaAnos());
        }
        double escalaY = valorRedondo(maxY);
        int divisoes = divisoes(escalaY);
        int esquerda = MARGEM + g.getFontMetrics().stringWidth(formatar(escalaY)) + 8;
        int direita = getWidth() - MARGEM;
        int base = getHeight() - 40;
        int altura = Math.max(base - topo, 40);
        double xMin = 0.5, xMax = maxX + 0.5; // cada inteiro no centro de uma faixa
        double larguraX = direita - esquerda;

        for (int i = 0; i <= divisoes; i++) {
            double valor = escalaY * i / divisoes;
            gradeY(g, esquerda, direita, base - (int) Math.round(altura * valor / escalaY), formatar(valor));
        }
        g.setColor(TEXTO_SECUNDARIO);
        for (int x = 1; x <= maxX; x++) {
            int px = (int) Math.round(esquerda + (x - xMin) / (xMax - xMin) * larguraX);
            String r = String.valueOf(x);
            g.drawString(r, px - g.getFontMetrics().stringWidth(r) / 2, base + 16);
        }
        String rotuloX = "x: " + eixoX;
        g.drawString(rotuloX, (esquerda + direita - g.getFontMetrics().stringWidth(rotuloX)) / 2, base + 32);

        // Tendencia (sobre os valores reais, sem o deslocamento visual)
        List<double[]> reais = new ArrayList<>();
        for (DashboardDAO.PontoMusico p : pontos) reais.add(new double[] {p.instrumentos(), p.mediaAnos()});
        double[] reta = Estatistica.retaTendencia(reais);
        if (reta != null) {
            double y1 = reta[0] + reta[1] * xMin, y2 = reta[0] + reta[1] * xMax;
            g.setColor(LARANJA);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {6f, 4f}, 0f));
            g.setClip(esquerda, topo, direita - esquerda, base - topo);
            g.draw(new Line2D.Double(esquerda, base - altura * y1 / escalaY, direita, base - altura * y2 / escalaY));
            g.setClip(null);
        }

        // Pontos (o mais proximo do mouse ganha contorno)
        DashboardDAO.PontoMusico maisProximo = null;
        double menorDistancia = 10 * 10;
        List<double[]> posicoes = new ArrayList<>();
        for (DashboardDAO.PontoMusico p : pontos) {
            double deslocamento = ((p.id() * 37) % 11 - 5) / 5.0 * DESLOCAMENTO_MAX;
            double px = esquerda + (p.instrumentos() + deslocamento - xMin) / (xMax - xMin) * larguraX;
            double py = base - altura * p.mediaAnos() / escalaY;
            posicoes.add(new double[] {px, py});
            if (mouse != null) {
                double d = (px - mouse.x) * (px - mouse.x) + (py - mouse.y) * (py - mouse.y);
                if (d < menorDistancia) { menorDistancia = d; maisProximo = p; }
            }
            dica(new Ellipse2D.Double(px - 7, py - 7, 14, 14), p.nome() + ": " + p.instrumentos()
                    + (p.instrumentos() == 1 ? " instrumento" : " instrumentos")
                    + " · " + String.format("%.1f", p.mediaAnos()) + " anos em média");
        }
        g.setStroke(new BasicStroke(1.5f));
        for (int i = 0; i < pontos.size(); i++) {
            double[] pos = posicoes.get(i);
            boolean destaque = pontos.get(i) == maisProximo;
            double raio = destaque ? 6.5 : 5;
            Ellipse2D ponto = new Ellipse2D.Double(pos[0] - raio, pos[1] - raio, raio * 2, raio * 2);
            g.setColor(new Color(AZUL.getRed(), AZUL.getGreen(), AZUL.getBlue(), destaque ? 255 : 170));
            g.fill(ponto);
            if (destaque) { g.setColor(TEXTO); g.draw(ponto); }
        }
        g.setColor(TEXTO_SECUNDARIO);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(esquerda, base, direita, base);
        g.dispose();
    }
}
