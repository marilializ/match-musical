import java.util.ArrayList;
import java.util.List;

/**
 * Calculos estatisticos que o MySQL nao oferece prontos (MEDIAN, MODE).
 * Os dados vem sempre do banco: aqui so se calcula sobre o resultado do SELECT.
 * Media, variancia e desvio padrao ficam no SQL (AVG, VAR_SAMP, STDDEV_SAMP).
 */
public final class Estatistica {
    private Estatistica() {
    }

    /**
     * Mediana a partir de frequencias ordenadas pelo valor: {valor, frequencia}.
     * Com n par, e a media dos dois valores centrais. null se nao ha dados.
     */
    public static Double mediana(List<int[]> frequencias, int n) {
        if (n <= 0) {
            return null;
        }
        int posicaoBaixa = (n - 1) / 2; // posicoes 0..n-1 na lista ordenada
        int posicaoAlta = n / 2;
        Integer baixa = null;
        Integer alta = null;
        int acumulado = 0;
        for (int[] faixa : frequencias) {
            int proximo = acumulado + faixa[1];
            if (baixa == null && posicaoBaixa < proximo) baixa = faixa[0];
            if (alta == null && posicaoAlta < proximo) { alta = faixa[0]; break; }
            acumulado = proximo;
        }
        return baixa == null || alta == null ? null : (baixa + alta) / 2.0;
    }

    /** Moda: todos os valores com a maior frequencia (empates entram todos). */
    public static List<Integer> modas(List<int[]> frequencias) {
        int maior = 0;
        for (int[] faixa : frequencias) maior = Math.max(maior, faixa[1]);
        List<Integer> modas = new ArrayList<>();
        if (maior == 0) return modas;
        for (int[] faixa : frequencias) if (faixa[1] == maior) modas.add(faixa[0]);
        return modas;
    }

    /**
     * Reta dos minimos quadrados y = a + b x, so para desenhar a tendencia no
     * grafico de dispersao. null quando x nao varia (reta vertical indefinida).
     */
    public static double[] retaTendencia(List<double[]> pontos) {
        int n = pontos.size();
        if (n < 2) return null;
        double somaX = 0, somaY = 0;
        for (double[] p : pontos) { somaX += p[0]; somaY += p[1]; }
        double mediaX = somaX / n, mediaY = somaY / n;
        double sxy = 0, sxx = 0;
        for (double[] p : pontos) {
            sxy += (p[0] - mediaX) * (p[1] - mediaY);
            sxx += (p[0] - mediaX) * (p[0] - mediaX);
        }
        if (sxx == 0) return null;
        double b = sxy / sxx;
        return new double[] {mediaY - b * mediaX, b};
    }

    /** Leitura do coeficiente de Pearson para a tela (faixas usuais da Estatistica). */
    public static String forcaCorrelacao(double r) {
        double modulo = Math.abs(r);
        String forca = modulo < 0.1 ? "praticamente nula"
                : modulo < 0.3 ? "fraca"
                : modulo < 0.7 ? "moderada"
                : "forte";
        if (modulo < 0.1) return forca;
        return forca + (r > 0 ? " positiva" : " negativa");
    }
}
