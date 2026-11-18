import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/**
 * Base dos graficos da Etapa 6 desenhados com Java2D (sem biblioteca externa):
 * linha (tendencia), histograma e dispersao. Mesmas cores e fontes do
 * GraficoBarrasPanel, para o dashboard ler como um sistema so.
 *
 * Interacao: o grafico guarda a posicao do mouse (destaque do item sob o
 * cursor) e cada area desenhada pode ter uma dica com o valor exato.
 */
abstract class GraficoBase extends JPanel {
    // Paleta validada para daltonismo (azul e laranja) + neutros
    static final Color AZUL = new Color(0x2a78d6);
    static final Color LARANJA = new Color(0xeb6834);
    static final Color ROXO = new Color(0x8c63bb);
    static final Color FUNDO = new Color(0xfcfcfb);
    static final Color TEXTO = new Color(0x1f1f1e);
    static final Color TEXTO_SECUNDARIO = new Color(0x6b6a63);
    static final Color GRADE = new Color(0xe6e5df);
    static final int MARGEM = 16;

    protected final String titulo;
    protected Point mouse;
    private final List<Shape> areasDica = new ArrayList<>();
    private final List<String> textosDica = new ArrayList<>();

    GraficoBase(String titulo) {
        this.titulo = titulo;
        setBackground(FUNDO);
        setToolTipText(""); // liga o sistema de tooltips do Swing
        setPreferredSize(new Dimension(420, 300));
        MouseAdapter rastreio = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent event) { mouse = event.getPoint(); repaint(); }
            @Override public void mouseExited(MouseEvent event) { mouse = null; repaint(); }
        };
        addMouseMotionListener(rastreio);
        addMouseListener(rastreio);
    }

    /** Prepara o pincel (antialias), zera as dicas e desenha o titulo. */
    protected Graphics2D preparar(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        areasDica.clear();
        textosDica.clear();
        g.setFont(fonte(Font.BOLD, 14f));
        g.setColor(TEXTO);
        g.drawString(titulo, MARGEM, MARGEM + 12);
        return g;
    }

    protected Font fonte(int estilo, float tamanho) {
        return getFont().deriveFont(estilo, tamanho);
    }

    /** Legenda em linha (quadradinho colorido + nome). Devolve o y abaixo dela. */
    protected int legenda(Graphics2D g, int y, String[] nomes, Color[] cores) {
        g.setFont(fonte(Font.PLAIN, 11f));
        int x = MARGEM;
        for (int i = 0; i < nomes.length; i++) {
            g.setColor(cores[i]);
            g.fill(new RoundRectangle2D.Double(x, y - 9, 10, 10, 3, 3));
            g.setColor(TEXTO_SECUNDARIO);
            g.drawString(nomes[i], x + 15, y);
            x += 15 + g.getFontMetrics().stringWidth(nomes[i]) + 18;
        }
        return y + 14;
    }

    protected void semDados(Graphics2D g, String mensagem, int y) {
        g.setFont(fonte(Font.PLAIN, 12f));
        g.setColor(TEXTO_SECUNDARIO);
        g.drawString(mensagem, MARGEM, y);
    }

    /** Linha de grade horizontal e o numero do eixo y, em cinza discreto. */
    protected void gradeY(Graphics2D g, int xEsquerda, int xDireita, int y, String rotulo) {
        g.setStroke(new BasicStroke(1f));
        g.setColor(GRADE);
        g.drawLine(xEsquerda, y, xDireita, y);
        g.setFont(fonte(Font.PLAIN, 11f));
        g.setColor(TEXTO_SECUNDARIO);
        g.drawString(rotulo, xEsquerda - 6 - g.getFontMetrics().stringWidth(rotulo), y + 4);
    }

    protected void dica(Shape area, String texto) {
        areasDica.add(area);
        textosDica.add(texto);
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        for (int i = 0; i < areasDica.size(); i++) {
            if (areasDica.get(i).contains(event.getPoint())) return textosDica.get(i);
        }
        return null;
    }

    /** Arredonda o maior valor para um numero "redondo" (1, 2, 2,5, 5 x 10^k). */
    static double valorRedondo(double maximo) {
        if (maximo <= 0) return 1;
        double potencia = Math.pow(10, Math.floor(Math.log10(maximo)));
        for (double passo : new double[] {1, 2, 2.5, 5, 10}) {
            if (passo * potencia >= maximo) return passo * potencia;
        }
        return 10 * potencia;
    }

    /** Divisoes do eixo que caem em numeros inteiros quando possivel. */
    static int divisoes(double escalaMax) {
        if (escalaMax <= 5 && escalaMax == Math.rint(escalaMax)) return (int) escalaMax;
        return escalaMax / 4 == Math.rint(escalaMax / 4) ? 4 : 5;
    }

    static String formatar(double valor) {
        return valor == Math.rint(valor) ? String.valueOf((long) valor) : String.format("%.1f", valor);
    }
}
