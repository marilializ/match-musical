import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Dashboard do SYNC com duas abas:
 *  - "Visao geral": 6 indicadores (+ taxa media de retribuicao, opcional),
 *    filtros e os 6 graficos da Etapa 6 (1 a 3: Pessoa 2; 4 a 6: Pessoa 6),
 *    alem do grafico de generos da consulta 7.
 *  - "Consultas": as 12 consultas (8 da Etapa 3 e 4 da Etapa 4), com o
 *    parametro de cada uma, a tabela de resultado com destaque e o SQL usado.
 *
 * Tudo o que aparece vem do banco (DashboardDAO): nada e fixo no codigo.
 * "Aplicar filtros" e "Atualizar dados" refazem todos os SELECTs.
 */
public class DashboardFrame extends JFrame {
    private static final Color TEXTO_SECUNDARIO = new Color(0x6b6a63);
    private static final Color TEXTO = new Color(0x1f1f1e);
    private static final Color DESTAQUE = new Color(0x1f5fae);
    private static final int GENEROS_NO_GRAFICO = 12;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final String TODAS = "Todas";
    private static final String TODOS = "Todos";

    private final DashboardDAO dao = new DashboardDAO();
    private final MusicoDAO musicoDAO = new MusicoDAO();

    // Aba "Visao geral": indicadores
    private final JLabel kpiMusicos = numeroGrande(), kpiGrupos = numeroGrande(), kpiVagas = numeroGrande();
    private final JLabel kpiMatches = numeroGrande(), kpiConvertidos = numeroGrande();
    private final JLabel kpiMensagens = numeroGrande(), kpiRetribuicao = numeroGrande();
    private final JLabel subMatches = legenda(" "), subConvertidos = legenda(" "), subMensagens = legenda(" ");

    // Graficos 1 a 3 (Pessoa 2) e o de generos (consulta 7)
    private final GraficoBarrasPanel graficoNiveis =
            new GraficoBarrasPanel("1. Músicos por nível de experiência", "Músicos");
    private final GraficoBarrasPanel graficoHabilidade =
            new GraficoBarrasPanel("2. Músicos por instrumento e experiência média", "Músicos", "Média (anos)");
    private final GraficoPizzaPanel graficoDecisoes = new GraficoPizzaPanel("3. Swipes por decisão");
    private final GraficoBarrasPanel graficoGeneros =
            new GraficoBarrasPanel("Oferta x procura: " + GENEROS_NO_GRAFICO
                    + " gêneros com mais grupos (consulta 7)", "Grupos", "Músicos interessados");
    private final JLabel resumoNiveis = legenda(" ");

    // Graficos 4 a 6 (Pessoa 6)
    private final GraficoLinhaPanel graficoSemanas =
            new GraficoLinhaPanel("4. Swipes e matches por semana", "swipes", "matches");
    private final GraficoHistogramaPanel graficoMensagens =
            new GraficoHistogramaPanel("5. Mensagens por match", "mensagens", "matches");
    private final GraficoDispersaoPanel graficoDispersao = new GraficoDispersaoPanel(
            "6. Instrumentos x experiência por músico", "nº de instrumentos que toca", "média de anos de experiência");
    private final JLabel estatMedia = estatistica(), estatMediana = estatistica(), estatModa = estatistica();
    private final JLabel estatVariancia = estatistica(), estatDesvio = estatistica();
    private final JLabel notaEstatistica = legenda(" ");
    private final JLabel resumoPearson = legenda(" ");

    // Filtros
    private final JTextField filtroInicio = new JTextField(10);
    private final JTextField filtroFim = new JTextField(10);
    private final JComboBox<String> filtroCategoria = new JComboBox<>();
    private final JComboBox<String> filtroHabilidade = new JComboBox<>();
    private final JComboBox<String> filtroSituacao = new JComboBox<>();
    private final JComboBox<Object> filtroInstrumento = new JComboBox<>();

    // Aba "Consultas"
    private final JComboBox<ConsultaSQL> seletorConsulta =
            new JComboBox<>(ConsultaSQL.TODAS.toArray(new ConsultaSQL[0]));
    private final JLabel rotuloParametro = new JLabel("Parâmetro:");
    private final JComboBox<Object> seletorParametro = new JComboBox<>();
    private final JLabel pergunta = new JLabel();
    private final JLabel legendaAuditoria = new JLabel();
    private final JLabel status = new JLabel(" ");
    private final DefaultTableModel modeloResultado = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTextArea textoSql = new JTextArea();
    private ConsultaSQL consultaExecutada;
    private boolean limpandoFiltros; // evita recarregar uma vez por combo ao limpar
    private final List<MusicoOpcao> musicos = new ArrayList<>();
    private final List<String> categorias = new ArrayList<>();
    private final List<DashboardDAO.Opcao> instrumentos = new ArrayList<>();

    /** Item do combo de musicos (mostra nome e id). */
    private record MusicoOpcao(long id, String nome, String nivel) {
        @Override
        public String toString() {
            return nome + " (#" + id + ", " + (nivel == null ? "sem nivel" : nivel) + ")";
        }
    }

    public DashboardFrame() {
        super("SYNC - Dashboard");
        setSize(1320, 880);
        setLocationRelativeTo(null);

        JTabbedPane abas = new JTabbedPane();
        abas.addTab("Visao geral", criarAbaVisaoGeral());
        abas.addTab("Consultas", criarAbaConsultas());
        add(abas);

        carregarFiltros();
        carregarVisaoGeral();
        atualizarConsultaSelecionada();
    }

    // ---------------------------------------------------------------- Visao geral

    private JPanel criarAbaVisaoGeral() {
        JPanel indicadores = new JPanel(new GridLayout(1, 7, 10, 0));
        indicadores.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        indicadores.add(cartao("Músicos", kpiMusicos, legenda("cadastrados")));
        indicadores.add(cartao("Grupos musicais", kpiGrupos, legenda("cadastrados")));
        indicadores.add(cartao("Vagas abertas", kpiVagas, legenda("agora")));
        indicadores.add(cartao("Matches ativos", kpiMatches, subMatches));
        indicadores.add(cartao("Matches convertidos", kpiConvertidos, subConvertidos));
        indicadores.add(cartao("Mensagens por match", kpiMensagens, subMensagens));
        indicadores.add(cartao("Taxa média de retribuição", kpiRetribuicao, legenda("geral · Etapa 5")));

        JButton aplicar = new JButton("Aplicar filtros");
        aplicar.addActionListener(event -> carregarVisaoGeral());
        JButton limpar = new JButton("Limpar");
        limpar.addActionListener(event -> limparFiltros());

        JPanel globais = new JPanel(new FlowLayout(FlowLayout.LEFT));
        globais.add(negrito("Período (indicadores e gráficos 3 a 5)"));
        globais.add(new JLabel("De (AAAA-MM-DD):")); globais.add(filtroInicio);
        globais.add(new JLabel("Até:")); globais.add(filtroFim);
        globais.add(aplicar); globais.add(limpar);

        JPanel porGrafico = new JPanel(new FlowLayout(FlowLayout.LEFT));
        porGrafico.add(negrito("Categorias"));
        porGrafico.add(new JLabel("1. Categoria principal:")); porGrafico.add(filtroCategoria);
        porGrafico.add(new JLabel("2. Habilidade:")); porGrafico.add(filtroHabilidade);
        porGrafico.add(new JLabel("5. Situação do match:")); porGrafico.add(filtroSituacao);
        porGrafico.add(new JLabel("6. Instrumento:")); porGrafico.add(filtroInstrumento);
        // Trocar um combo ja refaz os graficos: o filtro responde na hora
        for (JComboBox<?> combo : List.of(filtroCategoria, filtroHabilidade, filtroSituacao, filtroInstrumento)) {
            combo.addActionListener(event -> { if (combo.isShowing() && !limpandoFiltros) carregarVisaoGeral(); });
        }

        JPanel filtros = new JPanel();
        filtros.setLayout(new BoxLayout(filtros, BoxLayout.Y_AXIS));
        filtros.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        filtros.add(globais);
        filtros.add(porGrafico);

        JPanel topo = new JPanel(new BorderLayout());
        topo.add(indicadores, BorderLayout.NORTH);
        topo.add(filtros, BorderLayout.SOUTH);

        JPanel niveis = celula(rolagemVertical(graficoNiveis), resumoNiveis);
        JPanel estatisticas = new JPanel(new GridLayout(2, 3, 8, 2));
        estatisticas.add(estatMedia); estatisticas.add(estatMediana); estatisticas.add(estatModa);
        estatisticas.add(estatVariancia); estatisticas.add(estatDesvio); estatisticas.add(notaEstatistica);

        JPanel graficos = new LarguraDaJanela(new GridLayout(0, 3, 12, 12));
        graficos.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
        graficos.add(niveis);
        graficos.add(celula(rolagemVertical(graficoHabilidade), null));
        graficos.add(celula(graficoDecisoes, null));
        graficos.add(celula(graficoSemanas, null));
        graficos.add(celula(graficoMensagens, estatisticas));
        graficos.add(celula(graficoDispersao, resumoPearson));
        graficos.add(celula(rolagemVertical(graficoGeneros), null));

        JScrollPane rolagem = new JScrollPane(graficos);
        rolagem.setBorder(BorderFactory.createEmptyBorder());
        rolagem.getVerticalScrollBar().setUnitIncrement(24);

        JButton atualizar = new JButton("Atualizar dados");
        atualizar.addActionListener(event -> carregarVisaoGeral());
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JLabel fonte = legenda("Fonte: SELECTs do DashboardDAO sobre o banco match_musical (PreparedStatement, sem ORM).");
        rodape.add(fonte);
        rodape.add(atualizar);

        JPanel aba = new JPanel(new BorderLayout());
        aba.add(topo, BorderLayout.NORTH);
        aba.add(rolagem, BorderLayout.CENTER);
        aba.add(rodape, BorderLayout.SOUTH);
        return aba;
    }

    /**
     * Painel que, dentro de um JScrollPane, acompanha a largura visivel e so
     * rola na vertical (sem barra horizontal sobrando).
     */
    private static final class LarguraDaJanela extends JPanel implements Scrollable {
        LarguraDaJanela(java.awt.LayoutManager layout) { super(layout); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return r.height; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Grafico de barras com muitas categorias: rola so na vertical, na largura da celula. */
    private static JScrollPane rolagemVertical(JComponent grafico) {
        JPanel envoltorio = new LarguraDaJanela(new BorderLayout());
        envoltorio.add(grafico, BorderLayout.CENTER);
        JScrollPane rolagem = new JScrollPane(envoltorio,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        rolagem.setBorder(BorderFactory.createEmptyBorder());
        rolagem.getVerticalScrollBar().setUnitIncrement(16);
        return rolagem;
    }

    /** Uma celula da grade: o grafico em cima e, se houver, o resumo embaixo. */
    private static JPanel celula(JComponent grafico, JComponent rodape) {
        JPanel celula = new JPanel(new BorderLayout(0, 4));
        celula.setPreferredSize(new Dimension(360, rodape == null ? 320 : 360));
        celula.setBorder(BorderFactory.createLineBorder(new Color(0xe6e5df)));
        celula.add(grafico, BorderLayout.CENTER);
        if (rodape != null) {
            rodape.setBorder(BorderFactory.createEmptyBorder(2, 12, 8, 12));
            celula.add(rodape, BorderLayout.SOUTH);
        }
        return celula;
    }

    private static JLabel numeroGrande() {
        JLabel label = new JLabel("-");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 26f));
        label.setForeground(TEXTO);
        return label;
    }

    private static JLabel legenda(String texto) {
        JLabel label = new JLabel(texto);
        label.setForeground(TEXTO_SECUNDARIO);
        label.setFont(label.getFont().deriveFont(11.5f));
        return label;
    }

    private static JLabel negrito(String texto) {
        JLabel label = new JLabel(texto + "  ");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private static JLabel estatistica() {
        JLabel label = new JLabel("-");
        label.setForeground(TEXTO);
        return label;
    }

    private static JPanel cartao(String titulo, JLabel numero, JLabel rodape) {
        JPanel cartao = new JPanel();
        cartao.setLayout(new BoxLayout(cartao, BoxLayout.Y_AXIS));
        cartao.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xe6e5df)),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JLabel legenda = legenda(titulo);
        cartao.add(legenda);
        cartao.add(numero);
        cartao.add(rodape);
        return cartao;
    }

    private void carregarVisaoGeral() {
        try {
            LocalDate inicio = data(filtroInicio.getText());
            LocalDate fim = data(filtroFim.getText());
            if (inicio != null && fim != null && fim.isBefore(inicio))
                throw new IllegalArgumentException("A data final deve ser igual ou posterior à inicial.");
            boolean temPeriodo = inicio != null || fim != null;

            // Indicadores
            DashboardDAO.Painel painel = dao.painel(inicio, fim);
            kpiMusicos.setText(String.valueOf(painel.musicos()));
            kpiGrupos.setText(String.valueOf(painel.grupos()));
            kpiVagas.setText(String.valueOf(painel.vagasAbertas()));
            kpiMatches.setText(String.valueOf(painel.matchesAtivos()));
            subMatches.setText("de " + painel.matchesNoPeriodo() + (temPeriodo ? " no período" : " matches"));
            kpiConvertidos.setText(painel.percentualConvertidos() == null ? "-"
                    : decimal(painel.percentualConvertidos(), 1) + "%");
            subConvertidos.setText(painel.matchesConvertidos() + " viraram participação");
            kpiMensagens.setText(painel.mediaMensagensPorMatch() == null ? "-"
                    : decimal(painel.mediaMensagensPorMatch(), 2));
            subMensagens.setText(temPeriodo ? "média no período" : "média geral");
            kpiRetribuicao.setText(painel.taxaMediaRetribuicao() == null ? "-"
                    : decimal(painel.taxaMediaRetribuicao(), 1) + "%");

            // 1. Niveis (frequencia e moda), filtrado pela categoria principal
            DashboardDAO.Resultado niveis = dao.niveisPorCategoria(valor(filtroCategoria));
            List<String> nomesNivel = new ArrayList<>();
            List<double[]> qtdNivel = new ArrayList<>();
            List<int[]> freqNivel = new ArrayList<>();
            for (Object[] row : niveis.linhas()) {
                int qtd = ((Number) row[1]).intValue();
                nomesNivel.add(String.valueOf(row[0]));
                qtdNivel.add(new double[] {qtd});
                freqNivel.add(new int[] {freqNivel.size(), qtd});
            }
            graficoNiveis.setDados(nomesNivel, qtdNivel);
            List<String> modasNivel = Estatistica.modas(freqNivel).stream()
                    .map(nomesNivel::get).collect(Collectors.toList());
            resumoNiveis.setText(modasNivel.isEmpty() ? "Moda: -" : "Moda: " + String.join(" e ", modasNivel)
                    + " (" + freqNivel.stream().mapToInt(f -> f[1]).max().orElse(0) + " músicos)");

            // 2. Instrumentos com media de anos, filtrado pela habilidade
            DashboardDAO.Resultado instrumentosHabilidade = dao.instrumentosPorHabilidade(valor(filtroHabilidade));
            List<String> nomesHab = new ArrayList<>();
            List<double[]> valoresHab = new ArrayList<>();
            for (Object[] row : instrumentosHabilidade.linhas()) {
                if (((Number) row[1]).intValue() == 0) continue;
                nomesHab.add(String.valueOf(row[0]));
                valoresHab.add(new double[] {((Number) row[1]).doubleValue(),
                        row[2] == null ? 0 : ((Number) row[2]).doubleValue()});
            }
            graficoHabilidade.setDados(nomesHab, valoresHab);

            // 3. Swipes por decisao no periodo
            DashboardDAO.Resultado decisoes = dao.decisoesPorPeriodo(inicio, fim);
            List<String> nomesDecisoes = new ArrayList<>();
            List<Integer> qtdDecisoes = new ArrayList<>();
            for (Object[] row : decisoes.linhas()) {
                nomesDecisoes.add(String.valueOf(row[0]));
                qtdDecisoes.add(((Number) row[1]).intValue());
            }
            graficoDecisoes.setDados(nomesDecisoes, qtdDecisoes);

            // 4. Tendencia semanal de swipes e matches
            List<String> semanas = new ArrayList<>();
            List<double[]> valoresSemana = new ArrayList<>();
            for (DashboardDAO.Semana semana : dao.atividadeSemanal(inicio, fim)) {
                semanas.add(semana.inicio().format(DIA_MES));
                valoresSemana.add(new double[] {semana.swipes(), semana.matches()});
            }
            graficoSemanas.setDados(semanas, valoresSemana);

            // 5. Mensagens por match: histograma + medidas descritivas
            DashboardDAO.Distribuicao mensagens = dao.mensagensPorMatch(valor(filtroSituacao), inicio, fim);
            graficoMensagens.setDados(mensagens.frequencias(), mensagens.media(), mensagens.mediana());
            estatMedia.setText("Média: " + decimal(mensagens.media(), 2));
            estatMediana.setText("Mediana: " + decimal(mensagens.mediana(), 1));
            List<Integer> modas = mensagens.modas();
            estatModa.setText("Moda: " + (modas.isEmpty() ? "-"
                    : modas.stream().map(String::valueOf).collect(Collectors.joining(" e "))
                    + (modas.size() > 1 ? " (empate)" : "")));
            estatVariancia.setText("Variância: " + decimal(mensagens.variancia(), 2));
            estatDesvio.setText("Desvio padrão: " + decimal(mensagens.desvioPadrao(), 2));
            notaEstatistica.setText("amostrais (n − 1) · n = " + mensagens.n() + " matches");

            // 6. Dispersao instrumentos x experiencia + Pearson
            Integer idInstrumento = filtroInstrumento.getSelectedItem() instanceof DashboardDAO.Opcao opcao
                    ? opcao.id() : null;
            List<DashboardDAO.PontoMusico> pontos = dao.instrumentosVersusExperiencia(idInstrumento);
            graficoDispersao.setDados(pontos);
            Double r = dao.pearsonInstrumentosExperiencia(idInstrumento);
            resumoPearson.setText(r == null
                    ? "r de Pearson: indefinido (x ou y não variam) · n = " + pontos.size() + " músicos"
                    : "r de Pearson = " + decimal(r, 2) + " — correlação " + Estatistica.forcaCorrelacao(r)
                    + " · n = " + pontos.size() + " músicos");

            // Extra: generos da consulta 7 (os primeiros, ja ordenados por qtd_grupos)
            DashboardDAO.Resultado generos = dao.executar(ConsultaSQL.numero(7), null);
            int colGenero = generos.indice("genero");
            int colGrupos = generos.indice("qtd_grupos");
            int colMusicos = generos.indice("qtd_musicos_interessados");
            List<String> nomesGeneros = new ArrayList<>();
            List<double[]> pares = new ArrayList<>();
            for (Object[] linha : generos.linhas()) {
                if (nomesGeneros.size() == GENEROS_NO_GRAFICO) break;
                nomesGeneros.add(String.valueOf(linha[colGenero]));
                pares.add(new double[] {
                        ((Number) linha[colGrupos]).doubleValue(),
                        ((Number) linha[colMusicos]).doubleValue()});
            }
            graficoGeneros.setDados(nomesGeneros, pares);
        } catch (SQLException | IllegalArgumentException error) {
            mostrarErro(error);
        }
    }

    private static String decimal(Double valor, int casas) {
        return valor == null ? "-" : String.format(PT_BR, "%." + casas + "f", valor);
    }

    /** Valor de um combo de filtro: "Todas"/"Todos" vira null (sem filtro). */
    private static String valor(JComboBox<String> combo) {
        Object item = combo.getSelectedItem();
        return item == null || TODAS.equals(item) || TODOS.equals(item) ? null : item.toString();
    }

    private static LocalDate data(String texto) {
        if (texto.isBlank()) return null;
        try { return LocalDate.parse(texto.trim()); }
        catch (DateTimeParseException error) {
            throw new IllegalArgumentException("Use datas no formato AAAA-MM-DD.");
        }
    }

    private void limparFiltros() {
        limpandoFiltros = true;
        try {
            filtroInicio.setText("");
            filtroFim.setText("");
            filtroCategoria.setSelectedIndex(0);
            filtroHabilidade.setSelectedIndex(0);
            filtroSituacao.setSelectedIndex(0);
            filtroInstrumento.setSelectedIndex(0);
        } finally {
            limpandoFiltros = false;
        }
        carregarVisaoGeral();
    }

    private void carregarFiltros() {
        filtroCategoria.addItem(TODAS);
        filtroHabilidade.addItem(TODAS);
        filtroSituacao.addItem(TODAS);
        filtroInstrumento.addItem(TODOS);
        try {
            categorias.addAll(dao.categorias());
            for (String categoria : categorias) filtroCategoria.addItem(categoria);
            for (String habilidade : dao.habilidades()) filtroHabilidade.addItem(habilidade);
            for (String situacao : dao.situacoesMatch()) filtroSituacao.addItem(situacao);
            instrumentos.addAll(dao.instrumentos());
            for (DashboardDAO.Opcao instrumento : instrumentos) filtroInstrumento.addItem(instrumento);
            for (Musico musico : musicoDAO.listar()) {
                musicos.add(new MusicoOpcao(musico.getID(), musico.getNome(), musico.getNiveldeExperiencia()));
            }
        } catch (SQLException error) {
            mostrarErro(error);
        }
    }

    // ---------------------------------------------------------------- Consultas

    private JPanel criarAbaConsultas() {
        JButton executar = new JButton("Executar consulta");
        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filtros.add(new JLabel("Consulta:"));
        filtros.add(seletorConsulta);
        filtros.add(rotuloParametro);
        filtros.add(seletorParametro);
        filtros.add(executar);

        pergunta.setForeground(TEXTO_SECUNDARIO);
        pergunta.setBorder(BorderFactory.createEmptyBorder(0, 10, 2, 10));
        legendaAuditoria.setBorder(BorderFactory.createEmptyBorder(0, 10, 6, 10));
        legendaAuditoria.setText("<html>Cor por diagnóstico: "
                + "<span style='background:#dff3e4'>&nbsp;Consistente&nbsp;</span> "
                + "<span style='background:#fff1cc'>&nbsp;Match sem like mútuo&nbsp;</span> "
                + "<span style='background:#fbdcda'>&nbsp;Like mútuo sem match&nbsp;</span></html>");
        JPanel textos = new JPanel(new BorderLayout());
        textos.add(pergunta, BorderLayout.NORTH);
        textos.add(legendaAuditoria, BorderLayout.SOUTH);
        JPanel topo = new JPanel(new BorderLayout());
        topo.add(filtros, BorderLayout.NORTH);
        topo.add(textos, BorderLayout.SOUTH);

        JTable tabela = new JTable(modeloResultado);
        tabela.setAutoCreateRowSorter(true);
        tabela.setRowHeight(22);
        tabela.setDefaultRenderer(Object.class, new RealceRenderer());

        textoSql.setEditable(false);
        textoSql.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane painelSql = new JScrollPane(textoSql);
        painelSql.setBorder(BorderFactory.createTitledBorder("SQL enviado ao banco (os \"?\" recebem o parâmetro)"));

        JSplitPane divisao = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(tabela), painelSql);
        divisao.setResizeWeight(0.65);

        status.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        JPanel aba = new JPanel(new BorderLayout());
        aba.add(topo, BorderLayout.NORTH);
        aba.add(divisao, BorderLayout.CENTER);
        aba.add(status, BorderLayout.SOUTH);

        seletorConsulta.addActionListener(event -> atualizarConsultaSelecionada());
        executar.addActionListener(event -> executarConsulta());
        return aba;
    }

    /** Troca o combo de parametro conforme o que a consulta pede. */
    private void atualizarConsultaSelecionada() {
        ConsultaSQL consulta = (ConsultaSQL) seletorConsulta.getSelectedItem();
        if (consulta == null) return;
        pergunta.setText("Pergunta: " + consulta.pergunta());
        legendaAuditoria.setVisible(consulta.parametro() == ConsultaSQL.Parametro.DIAGNOSTICO);

        seletorParametro.removeAllItems();
        switch (consulta.parametro()) {
            case MUSICO -> musicos.forEach(seletorParametro::addItem);
            case CATEGORIA -> { seletorParametro.addItem(TODAS); categorias.forEach(seletorParametro::addItem); }
            case INSTRUMENTO -> { seletorParametro.addItem(TODOS); instrumentos.forEach(seletorParametro::addItem); }
            case DIAGNOSTICO -> { seletorParametro.addItem(TODOS); ConsultaSQL.DIAGNOSTICOS.forEach(seletorParametro::addItem); }
            case NENHUM -> seletorParametro.addItem("sem parâmetro");
        }
        boolean temParametro = consulta.parametro() != ConsultaSQL.Parametro.NENHUM;
        rotuloParametro.setText(temParametro ? consulta.parametro().rotulo + ":" : "Parâmetro:");
        seletorParametro.setEnabled(temParametro);

        textoSql.setText(consulta.sql());
        textoSql.setCaretPosition(0);
        modeloResultado.setRowCount(0);
        modeloResultado.setColumnCount(0);
        consultaExecutada = null;
        status.setText("Clique em \"Executar consulta\".");
    }

    /** O que vai para o "?": id (musico/instrumento), texto, ou null = sem filtro. */
    private Object valorParametro() {
        Object item = seletorParametro.getSelectedItem();
        if (item instanceof MusicoOpcao musico) return musico.id();
        if (item instanceof DashboardDAO.Opcao opcao) return opcao.id();
        if (item == null || TODAS.equals(item) || TODOS.equals(item) || "sem parâmetro".equals(item)) return null;
        return item.toString();
    }

    private void executarConsulta() {
        ConsultaSQL consulta = (ConsultaSQL) seletorConsulta.getSelectedItem();
        if (consulta == null) return;
        Object valor = valorParametro();
        try {
            DashboardDAO.Resultado resultado = dao.executar(consulta, valor);
            consultaExecutada = consulta;
            modeloResultado.setColumnIdentifiers(resultado.colunas().toArray());
            modeloResultado.setRowCount(0);
            for (Object[] linha : resultado.linhas()) modeloResultado.addRow(linha);
            String filtro = consulta.parametro() == ConsultaSQL.Parametro.NENHUM ? ""
                    : " · " + consulta.parametro().rotulo.toLowerCase(PT_BR) + ": "
                    + (valor == null ? "todos" : seletorParametro.getSelectedItem());
            status.setText(resultado.linhas().size() + " linha(s) retornada(s)" + filtro
                    + (consulta.colunaDestaque() == null ? "" : " · em destaque: " + consulta.colunaDestaque()));
        } catch (SQLException error) {
            mostrarErro(error);
        }
    }

    /**
     * Destaque visual da tabela: a coluna que responde a pergunta fica em
     * negrito azul; na auditoria (consulta 12) a linha ganha a cor do diagnostico.
     */
    private final class RealceRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tabela, Object valor, boolean selecionada,
                                                       boolean foco, int linha, int coluna) {
            Component celula = super.getTableCellRendererComponent(tabela, valor, selecionada, foco, linha, coluna);
            ConsultaSQL atual = consultaExecutada;
            boolean destaque = atual != null && tabela.getColumnName(coluna).equals(atual.colunaDestaque());
            celula.setFont(celula.getFont().deriveFont(destaque ? Font.BOLD : Font.PLAIN));
            if (selecionada) return celula;
            Color fundo = Color.WHITE;
            if (atual != null && atual.parametro() == ConsultaSQL.Parametro.DIAGNOSTICO) {
                int colunaDiagnostico = modeloResultado.findColumn("diagnostico");
                if (colunaDiagnostico >= 0) {
                    Object diagnostico = modeloResultado.getValueAt(tabela.convertRowIndexToModel(linha), colunaDiagnostico);
                    fundo = switch (String.valueOf(diagnostico)) {
                        case "Consistente" -> new Color(0xdff3e4);
                        case "Match sem like mutuo" -> new Color(0xfff1cc);
                        case "Like mutuo sem match" -> new Color(0xfbdcda);
                        default -> Color.WHITE;
                    };
                }
            }
            celula.setBackground(fundo);
            celula.setForeground(destaque ? DESTAQUE : TEXTO);
            return celula;
        }
    }

    private void mostrarErro(Exception error) {
        String message = error.getMessage();
        if (error instanceof SQLException sql) {
            System.err.println("Falha no dashboard: SQLState=" + sql.getSQLState()
                    + ", codigo=" + sql.getErrorCode() + ", detalhe=" + sql.getMessage());
            message = sql.getErrorCode() == 1305 || sql.getErrorCode() == 1146
                    ? "Um objeto SQL desta tela não existe no banco. Rode o reset (sql/00_reset.sql)."
                    : "Não foi possível consultar o banco. Confira a conexão e os objetos SQL.";
            if (sql.getSQLState() == null && message != null && sql.getMessage().startsWith("Escolha")) {
                message = sql.getMessage();
            }
        }
        JOptionPane.showMessageDialog(this, message, "Erro", JOptionPane.ERROR_MESSAGE);
    }
}
