import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;

/**
 * Histograma de uma variavel discreta (ex.: mensagens por match): uma barra
 * por valor, de min a max, sem pular valores (os que nao aparecem ficam com
 * barra zero). Linhas verticais marcam a media e a mediana.
 */
public class GraficoHistogramaPanel extends GraficoBase {
    private final String unidade;
    private final String contagem;
    private List<int[]> frequencias = List.of(); // {valor, frequencia}, ordenadas
    private Double media;
    private Double mediana;

    /** @param unidade nome do valor no eixo x (ex.: "mensagens"); contagem: o que se conta (ex.: "matches") */
    public GraficoHistogramaPanel(String titulo, String unidade, String contagem) {
        super(titulo);
        this.unidade = unidade;
        this.contagem = contagem;
    }

    public void setDados(List<int[]> frequencias, Double media, Double mediana) {
        this.frequencias = List.copyOf(frequencias);
        this.media = media;
        this.mediana = mediana;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = preparar(graphics);
        int topo = legenda(g, MARGEM + 34, new String[] {"média", "mediana"}, new Color[] {LARANJA, ROXO}) + 8;
        if (frequencias.isEmpty()) {
            semDados(g, "Nenhum match com esses filtros.", topo + 12);
            g.dispose();
            return;
        }

        int minimo = frequencias.get(0)[0];
        int maximoValor = frequencias.get(frequencias.size() - 1)[0];
        int classes = maximoValor - minimo + 1;
        int[] contagens = new int[classes];
        int maiorFrequencia = 0;
        for (int[] f : frequencias) {
            contagens[f[0] - minimo] = f[1];
            maiorFrequencia = Math.max(maiorFrequencia, f[1]);
        }
        double escala = valorRedondo(maiorFrequencia);
        int divisoes = divisoes(escala);

        g.setFont(fonte(Font.PLAIN, 11f));
        int esquerda = MARGEM + g.getFontMetrics().stringWidth(formatar(escala)) + 8;
        int direita = getWidth() - MARGEM;
        int base = getHeight() - 34;
        int altura = Math.max(base - topo, 40);
        for (int i = 0; i <= divisoes; i++) {
            double valor = escala * i / divisoes;
            gradeY(g, esquerda, direita, base - (int) Math.round(altura * valor / escala), formatar(valor));
        }

        double largura = (double) (direita - esquerda) / classes;
        for (int i = 0; i < classes; i++) {
            int x = (int) Math.round(esquerda + i * largura);
            int w = (int) Math.round(esquerda + (i + 1) * largura) - x - 2; // 2px entre barras
            int h = (int) Math.round(altura * contagens[i] / escala);
            Rectangle coluna = new Rectangle(x, topo, Math.max(w, 1), base - topo);
            boolean sobMouse = mouse != null && coluna.contains(mouse);
            if (h > 0) {
                g.setColor(sobMouse ? AZUL.darker() : AZUL);
                g.fillRect(x, base - h, Math.max(w, 1), h);
            }
            int valor = minimo + i;
            dica(coluna, valor + " " + unidade + ": " + contagens[i] + " " + contagem);
            g.setColor(TEXTO_SECUNDARIO);
            String r = String.valueOf(valor);
            g.drawString(r, x + w / 2 - g.getFontMetrics().stringWidth(r) / 2, base + 16);
        }

        // Media e mediana como guias verticais (posicao no centro da classe correspondente)
        marcar(g, media, LARANJA, minimo, esquerda, largura, topo, base, new float[] {5f, 4f});
        marcar(g, mediana, ROXO, minimo, esquerda, largura, topo, base, new float[] {2f, 3f});

        g.setColor(TEXTO_SECUNDARIO);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(esquerda, base, direita, base);
        g.dispose();
    }

    private static void marcar(Graphics2D g, Double valor, Color cor, int minimo, int esquerda,
                               double largura, int topo, int base, float[] traco) {
        if (valor == null) return;
        int x = (int) Math.round(esquerda + (valor - minimo + 0.5) * largura);
        g.setColor(cor);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, traco, 0f));
        g.drawLine(x, topo, x, base);
    }
}
