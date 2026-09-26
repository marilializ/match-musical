import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;

/**
 * Dashboard do SYNC com duas abas:
 *  - "Visao geral": indicadores (totais) + 2 graficos gerados a partir das consultas 2 e 7.
 *  - "Consultas": escolhe uma das 8 consultas, executa e mostra o resultado e o SQL usado.
 */
public class DashboardFrame extends JFrame {
    private static final Color TEXTO_SECUNDARIO = new Color(0x6b6a63);
    private static final int GENEROS_NO_GRAFICO = 12;

    private final DashboardDAO dao = new DashboardDAO();
    private final MusicoDAO musicoDAO = new MusicoDAO();

    // Aba "Visao geral"
    private final JLabel totalMusicos = numeroGrande();
    private final JLabel totalGrupos = numeroGrande();
    private final JLabel totalVagas = numeroGrande();
    private final JLabel totalMatches = numeroGrande();
    private final GraficoBarrasPanel graficoInstrumentos =
            new GraficoBarrasPanel("Musicos por instrumento (consulta 2)", "Musicos");
    private final GraficoBarrasPanel graficoGeneros =
            new GraficoBarrasPanel("Oferta x procura: " + GENEROS_NO_GRAFICO
                    + " generos com mais grupos (consulta 7)", "Grupos", "Musicos interessados");

    // Aba "Consultas"
    private final JComboBox<ConsultaSQL> seletorConsulta =
            new JComboBox<>(ConsultaSQL.TODAS.toArray(new ConsultaSQL[0]));
    private final JComboBox<MusicoOpcao> seletorMusico = new JComboBox<>();
    private final JLabel pergunta = new JLabel();
    private final JLabel status = new JLabel(" ");
    private final DefaultTableModel modeloResultado = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTextArea textoSql = new JTextArea();

    /** Item do combo de musicos (mostra nome e id). */
    private record MusicoOpcao(long id, String nome, String nivel) {
        @Override
        public String toString() {
            return nome + " (#" + id + ", " + (nivel == null ? "sem nivel" : nivel) + ")";
        }
    }

    public DashboardFrame() {
        super("SYNC - Dashboard");
        setSize(1150, 760);
        setLocationRelativeTo(null);

        JTabbedPane abas = new JTabbedPane();
        abas.addTab("Visao geral", criarAbaVisaoGeral());
        abas.addTab("Consultas", criarAbaConsultas());
        add(abas);

        carregarVisaoGeral();
        carregarMusicos();
        atualizarConsultaSelecionada();
    }

    // ---------------------------------------------------------------- Visao geral

    private JPanel criarAbaVisaoGeral() {
        JPanel indicadores = new JPanel(new GridLayout(1, 4, 12, 0));
        indicadores.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        indicadores.add(cartao("Musicos cadastrados", totalMusicos));
        indicadores.add(cartao("Grupos musicais", totalGrupos));
        indicadores.add(cartao("Vagas abertas", totalVagas));
        indicadores.add(cartao("Matches ativos", totalMatches));

        JPanel graficos = new JPanel(new GridLayout(1, 2, 12, 0));
        graficos.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        graficos.add(new JScrollPane(graficoInstrumentos));
        graficos.add(new JScrollPane(graficoGeneros));

        JButton atualizar = new JButton("Atualizar dados");
        atualizar.addActionListener(event -> carregarVisaoGeral());
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rodape.add(atualizar);

        JPanel aba = new JPanel(new BorderLayout());
        aba.add(indicadores, BorderLayout.NORTH);
        aba.add(graficos, BorderLayout.CENTER);
        aba.add(rodape, BorderLayout.SOUTH);
        return aba;
    }

    private static JLabel numeroGrande() {
        JLabel label = new JLabel("-");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 28f));
        return label;
    }

    private static JPanel cartao(String titulo, JLabel numero) {
        JPanel cartao = new JPanel();
        cartao.setLayout(new BoxLayout(cartao, BoxLayout.Y_AXIS));
        cartao.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xe6e5df)),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        JLabel legenda = new JLabel(titulo);
        legenda.setForeground(TEXTO_SECUNDARIO);
        cartao.add(legenda);
        cartao.add(numero);
        return cartao;
    }

    private void carregarVisaoGeral() {
        try {
            DashboardDAO.Indicadores numeros = dao.indicadores();
            totalMusicos.setText(String.valueOf(numeros.musicos()));
            totalGrupos.setText(String.valueOf(numeros.grupos()));
            totalVagas.setText(String.valueOf(numeros.vagasAbertas()));
            totalMatches.setText(String.valueOf(numeros.matchesAtivos()));

            // Grafico 1: consulta 2 (so instrumentos com pelo menos 1 musico)
            DashboardDAO.Resultado instrumentos = dao.executar(ConsultaSQL.numero(2), null);
            int colInstrumento = instrumentos.indice("instrumento");
            int colQtd = instrumentos.indice("qtd_musicos");
            List<String> nomes = new ArrayList<>();
            List<double[]> quantidades = new ArrayList<>();
            for (Object[] linha : instrumentos.linhas()) {
                double qtd = ((Number) linha[colQtd]).doubleValue();
                if (qtd > 0) {
                    nomes.add(String.valueOf(linha[colInstrumento]));
                    quantidades.add(new double[] {qtd});
                }
            }
            graficoInstrumentos.setDados(nomes, quantidades);

            // Grafico 2: consulta 7 (ja vem ordenada por qtd_grupos, pega os primeiros)
            DashboardDAO.Resultado generos = dao.executar(ConsultaSQL.numero(7), null);
            int colGenero = generos.indice("genero");
            int colGrupos = generos.indice("qtd_grupos");
            int colMusicos = generos.indice("qtd_musicos_interessados");
            List<String> nomesGeneros = new ArrayList<>();
            List<double[]> pares = new ArrayList<>();
            for (Object[] linha : generos.linhas()) {
                if (nomesGeneros.size() == GENEROS_NO_GRAFICO) {
                    break;
                }
                nomesGeneros.add(String.valueOf(linha[colGenero]));
                pares.add(new double[] {
                        ((Number) linha[colGrupos]).doubleValue(),
                        ((Number) linha[colMusicos]).doubleValue()});
            }
            graficoGeneros.setDados(nomesGeneros, pares);
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
        filtros.add(new JLabel("Musico (consulta 6):"));
        filtros.add(seletorMusico);
        filtros.add(executar);

        pergunta.setForeground(TEXTO_SECUNDARIO);
        pergunta.setBorder(BorderFactory.createEmptyBorder(0, 10, 6, 10));
        JPanel topo = new JPanel(new BorderLayout());
        topo.add(filtros, BorderLayout.NORTH);
        topo.add(pergunta, BorderLayout.SOUTH);

        JTable tabela = new JTable(modeloResultado);
        tabela.setAutoCreateRowSorter(true);

        textoSql.setEditable(false);
        textoSql.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane painelSql = new JScrollPane(textoSql);
        painelSql.setBorder(BorderFactory.createTitledBorder("SQL enviado ao banco"));

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

    private void carregarMusicos() {
        try {
            seletorMusico.removeAllItems();
            for (Musico musico : musicoDAO.listar()) {
                seletorMusico.addItem(new MusicoOpcao(
                        musico.getID(), musico.getNome(), musico.getNiveldeExperiencia()));
            }
        } catch (SQLException error) {
            mostrarErro(error);
        }
    }

    private void atualizarConsultaSelecionada() {
        ConsultaSQL consulta = (ConsultaSQL) seletorConsulta.getSelectedItem();
        if (consulta == null) {
            return;
        }
        pergunta.setText("Pergunta: " + consulta.pergunta());
        seletorMusico.setEnabled(consulta.usaMusico());
        textoSql.setText(consulta.sql());
        textoSql.setCaretPosition(0);
        modeloResultado.setRowCount(0);
        modeloResultado.setColumnCount(0);
        status.setText("Clique em \"Executar consulta\".");
    }

    private void executarConsulta() {
        ConsultaSQL consulta = (ConsultaSQL) seletorConsulta.getSelectedItem();
        if (consulta == null) {
            return;
        }
        MusicoOpcao musico = (MusicoOpcao) seletorMusico.getSelectedItem();
        Long idMusico = musico == null ? null : musico.id();
        try {
            DashboardDAO.Resultado resultado = dao.executar(consulta, idMusico);
            modeloResultado.setColumnIdentifiers(resultado.colunas().toArray());
            modeloResultado.setRowCount(0);
            for (Object[] linha : resultado.linhas()) {
                modeloResultado.addRow(linha);
            }
            String sufixo = consulta.usaMusico() && musico != null ? " para " + musico.nome() : "";
            status.setText(resultado.linhas().size() + " linha(s) retornada(s)" + sufixo + ".");
        } catch (SQLException error) {
            mostrarErro(error);
        }
    }

    private void mostrarErro(Exception error) {
        JOptionPane.showMessageDialog(this, error.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
    }
}
