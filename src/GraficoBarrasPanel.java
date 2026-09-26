import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/**
 * Grafico de barras horizontais desenhado com Java2D (sem biblioteca externa).
 * Aceita 1 serie (ex.: musicos por instrumento) ou 2 series lado a lado
 * (ex.: grupos x musicos interessados por genero).
 *
 * Barras horizontais porque os nomes das categorias sao longos e ficam
 * legiveis no eixo vertical. Passando o mouse sobre uma barra aparece
 * o valor exato (tooltip).
 */
public class GraficoBarrasPanel extends JPanel {
    // Cores das series (paleta validada para daltonismo: azul e laranja)
    private static final Color[] CORES_SERIES = {new Color(0x2a78d6), new Color(0xeb6834)};
    private static final Color FUNDO = new Color(0xfcfcfb);
    private static final Color TEXTO = new Color(0x1f1f1e);
    private static final Color TEXTO_SECUNDARIO = new Color(0x6b6a63);
    private static final Color GRADE = new Color(0xe6e5df);

    private static final int ESPESSURA_BARRA = 12;
    private static final int ESPACO_ENTRE_SERIES = 2;
    private static final int ESPACO_ENTRE_CATEGORIAS = 10;
    private static final int MARGEM = 16;

    private final String titulo;
    private final String[] nomesSeries;
    private List<String> categorias = List.of();
    private List<double[]> valores = List.of();
    private final List<Rectangle> areasTooltip = new ArrayList<>();
    private final List<String> textosTooltip = new ArrayList<>();

    public GraficoBarrasPanel(String titulo, String... nomesSeries) {
        this.titulo = titulo;
        this.nomesSeries = nomesSeries;
        setBackground(FUNDO);
        setToolTipText(""); // liga o sistema de tooltips do Swing para este painel
    }

    /** Recebe as categorias e, para cada uma, um valor por serie. */
    public void setDados(List<String> categorias, List<double[]> valores) {
        this.categorias = List.copyOf(categorias);
        this.valores = List.copyOf(valores);
        revalidate();
        repaint();
    }

    private int alturaTopo() {
        return nomesSeries.length > 1 ? 64 : 44; // titulo (+ legenda se tiver 2 series)
    }

    private int alturaCategoria() {
        int series = nomesSeries.length;
        return series * ESPESSURA_BARRA + (series - 1) * ESPACO_ENTRE_SERIES + ESPACO_ENTRE_CATEGORIAS;
    }

    @Override
    public Dimension getPreferredSize() {
        int altura = alturaTopo() + categorias.size() * alturaCategoria() + 32;
        return new Dimension(460, Math.max(altura, 160));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        areasTooltip.clear();
        textosTooltip.clear();

        Font fonteBase = getFont().deriveFont(Font.PLAIN, 12f);
        Font fonteTitulo = fonteBase.deriveFont(Font.BOLD, 14f);
        Font fontePequena = fonteBase.deriveFont(11f);

        // Titulo
        g.setFont(fonteTitulo);
        g.setColor(TEXTO);
        g.drawString(titulo, MARGEM, MARGEM + 12);

        // Legenda (so quando ha 2 ou mais series)
        if (nomesSeries.length > 1) {
            g.setFont(fontePequena);
            int x = MARGEM;
            for (int serie = 0; serie < nomesSeries.length; serie++) {
                g.setColor(CORES_SERIES[serie]);
                g.fill(new RoundRectangle2D.Double(x, MARGEM + 24, 10, 10, 3, 3));
                g.setColor(TEXTO_SECUNDARIO);
                g.drawString(nomesSeries[serie], x + 15, MARGEM + 33);
                x += 15 + g.getFontMetrics().stringWidth(nomesSeries[serie]) + 18;
            }
        }

        if (categorias.isEmpty()) {
            g.setFont(fonteBase);
            g.setColor(TEXTO_SECUNDARIO);
            g.drawString("Sem dados para mostrar.", MARGEM, alturaTopo() + 20);
            g.dispose();
            return;
        }

        // Largura reservada para os nomes das categorias (lado esquerdo)
        g.setFont(fonteBase);
        FontMetrics medidas = g.getFontMetrics();
        int larguraRotulos = 0;
        for (String categoria : categorias) {
            larguraRotulos = Math.max(larguraRotulos, medidas.stringWidth(categoria));
        }
        int xBase = MARGEM + larguraRotulos + 10;
        int larguraGrafico = Math.max(getWidth() - xBase - MARGEM - 36, 60);
        int yInicio = alturaTopo();
        int yFim = yInicio + categorias.size() * alturaCategoria();

        // Escala: arredonda o maior valor para um numero "redondo"
        double maximo = 0;
        for (double[] linha : valores) {
            for (double valor : linha) {
                maximo = Math.max(maximo, valor);
            }
        }
        double escalaMax = valorRedondo(maximo);
        // Marcas do eixo em numeros inteiros (ex.: 0-1-2-3-4-5 ou 0-5-10-15-20)
        int divisoes = 4;
        if (escalaMax / divisoes != Math.rint(escalaMax / divisoes)) {
            divisoes = escalaMax <= 10 ? (int) escalaMax : 5;
        }

        // Grade vertical + numeros do eixo (discretos, em cinza)
        g.setFont(fontePequena);
        FontMetrics medidasPequenas = g.getFontMetrics();
        for (int i = 0; i <= divisoes; i++) {
            double valor = escalaMax * i / divisoes;
            int x = xBase + (int) Math.round(larguraGrafico * valor / escalaMax);
            g.setColor(GRADE);
            g.setStroke(new BasicStroke(1f));
            g.drawLine(x, yInicio - 4, x, yFim);
            g.setColor(TEXTO_SECUNDARIO);
            String texto = formatar(valor);
            g.drawString(texto, x - medidasPequenas.stringWidth(texto) / 2, yFim + 16);
        }

        // Barras
        for (int indice = 0; indice < categorias.size(); indice++) {
            int yCategoria = yInicio + indice * alturaCategoria();
            double[] linha = valores.get(indice);

            g.setFont(fonteBase);
            g.setColor(TEXTO);
            int yTexto = yCategoria + (alturaCategoria() - ESPACO_ENTRE_CATEGORIAS) / 2 + medidas.getAscent() / 2 - 1;
            g.drawString(categorias.get(indice), xBase - 10 - medidas.stringWidth(categorias.get(indice)), yTexto);

            for (int serie = 0; serie < nomesSeries.length; serie++) {
                double valor = linha[serie];
                int y = yCategoria + serie * (ESPESSURA_BARRA + ESPACO_ENTRE_SERIES);
                int largura = (int) Math.round(larguraGrafico * valor / escalaMax);
                if (largura > 0) {
                    g.setColor(CORES_SERIES[serie]);
                    g.fill(barraComPontaArredondada(xBase, y, largura, ESPESSURA_BARRA));
                }
                // Valor escrito na ponta da barra (em cor de texto, nao na cor da serie)
                g.setFont(fontePequena);
                g.setColor(TEXTO_SECUNDARIO);
                g.drawString(formatar(valor), xBase + largura + 5, y + ESPESSURA_BARRA - 2);

                // Area do tooltip: a linha inteira da barra, maior que a propria barra
                areasTooltip.add(new Rectangle(xBase - larguraRotulos - 10, y - 1,
                        larguraRotulos + 10 + larguraGrafico + 36, ESPESSURA_BARRA + 2));
                textosTooltip.add(categorias.get(indice) + " - " + nomesSeries[serie] + ": " + formatar(valor));
            }
        }

        // Linha de base (eixo zero)
        g.setColor(TEXTO_SECUNDARIO);
        g.drawLine(xBase, yInicio - 4, xBase, yFim);
        g.dispose();
    }

    /** Barra presa no eixo (canto reto) com a ponta direita arredondada (4px). */
    private static Area barraComPontaArredondada(int x, int y, int largura, int altura) {
        Area barra = new Area(new RoundRectangle2D.Double(x, y, largura, altura, 8, 8));
        barra.add(new Area(new Rectangle2D.Double(x, y, Math.min(largura, 4), altura)));
        return barra;
    }

    private static double valorRedondo(double maximo) {
        if (maximo <= 0) {
            return 1;
        }
        double potencia = Math.pow(10, Math.floor(Math.log10(maximo)));
        for (double passo : new double[] {1, 2, 2.5, 5, 10}) {
            if (passo * potencia >= maximo) {
                return passo * potencia;
            }
        }
        return 10 * potencia;
    }

    private static String formatar(double valor) {
        return valor == Math.rint(valor) ? String.valueOf((long) valor) : String.format("%.1f", valor);
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        for (int i = 0; i < areasTooltip.size(); i++) {
            if (areasTooltip.get(i).contains(event.getPoint())) {
                return textosTooltip.get(i);
            }
        }
        return null;
    }
}
