import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * Grafico de linhas (tendencia no tempo) com ate 3 series sobre o mesmo eixo x.
 * Passando o mouse, a coluna sob o cursor ganha uma guia vertical e a dica
 * mostra todos os valores daquele ponto.
 */
public class GraficoLinhaPanel extends GraficoBase {
    private static final Color[] CORES = {AZUL, LARANJA, ROXO};

    private final String[] series;
    private List<String> rotulos = List.of();
    private List<double[]> valores = List.of();

    public GraficoLinhaPanel(String titulo, String... series) {
        super(titulo);
        this.series = series;
    }

    /** Um rotulo por ponto do eixo x e, para cada um, um valor por serie. */
    public void setDados(List<String> rotulos, List<double[]> valores) {
        this.rotulos = List.copyOf(rotulos);
        this.valores = List.copyOf(valores);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = preparar(graphics);
        int topo = legenda(g, MARGEM + 34, series, CORES) + 8;
        if (rotulos.isEmpty()) {
            semDados(g, "Sem dados no período.", topo + 12);
            g.dispose();
            return;
        }

        double maximo = 0;
        for (double[] linha : valores) for (double v : linha) maximo = Math.max(maximo, v);
        double escala = valorRedondo(maximo);
        int divisoes = divisoes(escala);

        g.setFont(fonte(Font.PLAIN, 11f));
        int larguraNumeros = g.getFontMetrics().stringWidth(formatar(escala));
        int esquerda = MARGEM + larguraNumeros + 8;
        int direita = getWidth() - MARGEM;
        int base = getHeight() - 34;
        int altura = Math.max(base - topo, 40);

        for (int i = 0; i <= divisoes; i++) {
            double valor = escala * i / divisoes;
            gradeY(g, esquerda, direita, base - (int) Math.round(altura * valor / escala), formatar(valor));
        }

        int n = rotulos.size();
        double passo = n == 1 ? 0 : (double) (direita - esquerda - 12) / (n - 1);
        int x0 = n == 1 ? (esquerda + direita) / 2 : esquerda + 6;

        // Coluna sob o mouse: guia vertical + dica com todos os valores
        int destacado = -1;
        for (int i = 0; i < n; i++) {
            int x = (int) Math.round(x0 + i * passo);
            int meia = (int) Math.max(passo / 2, 6);
            Rectangle coluna = new Rectangle(x - meia, topo, meia * 2, base - topo);
            StringBuilder texto = new StringBuilder(rotulos.get(i)).append(":");
            for (int s = 0; s < series.length; s++) {
                texto.append(s == 0 ? " " : " · ").append(formatar(valores.get(i)[s])).append(' ').append(series[s]);
            }
            dica(coluna, texto.toString());
            if (mouse != null && coluna.contains(mouse)) destacado = i;
        }
        if (destacado >= 0) {
            int x = (int) Math.round(x0 + destacado * passo);
            g.setColor(GRADE.darker());
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {3f, 3f}, 0f));
            g.drawLine(x, topo, x, base);
        }

        // Rotulos do eixo x: so os que cabem sem encostar
        g.setFont(fonte(Font.PLAIN, 11f));
        int larguraRotulo = 0;
        for (String r : rotulos) larguraRotulo = Math.max(larguraRotulo, g.getFontMetrics().stringWidth(r));
        int salto = passo <= 0 ? 1 : (int) Math.ceil((larguraRotulo + 8) / passo);
        g.setColor(TEXTO_SECUNDARIO);
        for (int i = 0; i < n; i += Math.max(salto, 1)) {
            int x = (int) Math.round(x0 + i * passo);
            String r = rotulos.get(i);
            g.drawString(r, x - g.getFontMetrics().stringWidth(r) / 2, base + 16);
        }

        // Series
        for (int s = 0; s < series.length; s++) {
            Path2D linha = new Path2D.Double();
            for (int i = 0; i < n; i++) {
                double x = x0 + i * passo;
                double y = base - altura * valores.get(i)[s] / escala;
                if (i == 0) linha.moveTo(x, y); else linha.lineTo(x, y);
            }
            g.setColor(CORES[s % CORES.length]);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(linha);
            for (int i = 0; i < n; i++) {
                double x = x0 + i * passo;
                double y = base - altura * valores.get(i)[s] / escala;
                double raio = i == destacado ? 4.5 : 3;
                g.fill(new Ellipse2D.Double(x - raio, y - raio, raio * 2, raio * 2));
            }
        }
        g.setColor(TEXTO_SECUNDARIO);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(esquerda, base, direita, base);
        g.dispose();
    }
}
