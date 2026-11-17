import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.sql.SQLException;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/** Tela administrativa para Swipe e Match. Datas e chaves sao geradas pelo banco. */
public class SwipeMatchFrame extends JFrame {
    private final SwipeMatchDAO dao = new SwipeMatchDAO();
    private final DefaultTableModel swipes = model("ID", "Data/hora", "Decisão", "Origem", "Destino");
    private final DefaultTableModel matches = model("ID", "Criado em", "Situação", "Usuário A", "Usuário B");
    private final DefaultTableModel log = model("ID log", "Anterior", "Nova", "Data/hora");
    private final JTable tabelaSwipes = new JTable(swipes);
    private final JTable tabelaMatches = new JTable(matches);
    private final JTextField origem = new JTextField(6);
    private final JTextField destino = new JTextField(6);
    private final JTextField usuarioA = new JTextField(6);
    private final JTextField usuarioB = new JTextField(6);
    private final JComboBox<String> decisao = new JComboBox<>(new String[] {"Like", "Dislike", "Super Like"});
    private final JComboBox<String> situacao = new JComboBox<>(new String[] {"Ativo", "Encerrado", "Convertido", "Pendente"});

    public SwipeMatchFrame() {
        super("SYNC - Swipes e Matches");
        setSize(1050, 700);
        setLocationRelativeTo(null);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Swipes", abaSwipe());
        tabs.addTab("Matches e log", abaMatch());
        add(tabs);
        tabelaSwipes.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && tabelaSwipes.getSelectedRow() >= 0) {
                int row = tabelaSwipes.convertRowIndexToModel(tabelaSwipes.getSelectedRow());
                decisao.setSelectedItem(swipes.getValueAt(row, 2));
                origem.setText(String.valueOf(swipes.getValueAt(row, 3)));
                destino.setText(String.valueOf(swipes.getValueAt(row, 4)));
            }
        });
        tabelaMatches.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && tabelaMatches.getSelectedRow() >= 0) {
                int row = tabelaMatches.convertRowIndexToModel(tabelaMatches.getSelectedRow());
                situacao.setSelectedItem(matches.getValueAt(row, 2));
                usuarioA.setText(String.valueOf(matches.getValueAt(row, 3)));
                usuarioB.setText(String.valueOf(matches.getValueAt(row, 4)));
                carregarLog((int) matches.getValueAt(row, 0));
            }
        });
        recarregar();
    }

    private JPanel abaSwipe() {
        tabelaSwipes.setAutoCreateRowSorter(true);
        JPanel fields = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fields.add(new JLabel("Usuário origem (ID):")); fields.add(origem);
        fields.add(new JLabel("Destino (ID):")); fields.add(destino);
        fields.add(new JLabel("Decisão:")); fields.add(decisao);
        JButton insert = new JButton("Inserir");
        JButton update = new JButton("Alterar decisão");
        JButton delete = new JButton("Excluir");
        JButton refresh = new JButton("Recarregar");
        fields.add(insert); fields.add(update); fields.add(delete); fields.add(refresh);
        insert.addActionListener(e -> executar(() -> {
            int id = dao.inserirSwipe((String) decisao.getSelectedItem(), number(origem), number(destino));
            info("Swipe #" + id + " inserido. Se o interesse for mútuo, o trigger cria o match.");
        }));
        update.addActionListener(e -> executar(() -> {
            dao.alterarSwipe(selected(tabelaSwipes, swipes), (String) decisao.getSelectedItem());
            info("Decisão atualizada. O trigger de criação atua apenas em INSERT.");
        }));
        delete.addActionListener(e -> confirmar("Excluir o swipe selecionado?", () -> {
            dao.excluirSwipe(selected(tabelaSwipes, swipes));
            info("Swipe excluído. Matches já existentes permanecem.");
        }));
        refresh.addActionListener(e -> recarregar());
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(fields, BorderLayout.NORTH);
        panel.add(new JScrollPane(tabelaSwipes), BorderLayout.CENTER);
        return panel;
    }

    private JPanel abaMatch() {
        tabelaMatches.setAutoCreateRowSorter(true);
        JPanel fields = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fields.add(new JLabel("Usuário A (ID):")); fields.add(usuarioA);
        fields.add(new JLabel("Usuário B (ID):")); fields.add(usuarioB);
        fields.add(new JLabel("Situação:")); fields.add(situacao);
        JButton insert = new JButton("Inserir");
        JButton update = new JButton("Alterar situação");
        JButton delete = new JButton("Excluir");
        JButton refresh = new JButton("Recarregar");
        fields.add(insert); fields.add(update); fields.add(delete); fields.add(refresh);
        insert.addActionListener(e -> executar(() -> {
            int id = dao.inserirMatch(number(usuarioA), number(usuarioB), (String) situacao.getSelectedItem());
            info("Match #" + id + " inserido.");
        }));
        update.addActionListener(e -> executar(() -> {
            dao.alterarMatch(selected(tabelaMatches, matches), (String) situacao.getSelectedItem());
            info("Situação atualizada. Confira o log abaixo.");
        }));
        delete.addActionListener(e -> confirmar("Excluir o match selecionado? Mensagens associadas serão removidas.", () -> {
            dao.excluirMatch(selected(tabelaMatches, matches));
            info("Match excluído.");
        }));
        refresh.addActionListener(e -> recarregar());
        JPanel center = new JPanel(new GridLayout(2, 1));
        center.add(new JScrollPane(tabelaMatches));
        JPanel logs = new JPanel(new BorderLayout());
        logs.add(new JLabel("Histórico do match selecionado"), BorderLayout.NORTH);
        logs.add(new JScrollPane(new JTable(log)), BorderLayout.CENTER);
        center.add(logs);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(fields, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private void carregarLog(int id) {
        try {
            log.setRowCount(0);
            for (Object[] row : dao.listarLog(id)) log.addRow(row);
        } catch (SQLException error) { mostrarErro(error); }
    }

    private void recarregar() {
        try {
            int matchSelecionado = tabelaMatches.getSelectedRow() < 0 ? 0
                    : (int) matches.getValueAt(tabelaMatches.convertRowIndexToModel(tabelaMatches.getSelectedRow()), 0);
            swipes.setRowCount(0);
            for (SwipeMatchDAO.Swipe s : dao.listarSwipes())
                swipes.addRow(new Object[] {s.id(), s.dataHora(), s.decisao(), s.origem(), s.destino()});
            matches.setRowCount(0);
            for (SwipeMatchDAO.Match m : dao.listarMatches())
                matches.addRow(new Object[] {m.id(), m.dataCriacao(), m.situacao(), m.usuarioA(), m.usuarioB()});
            boolean restored = false;
            for (int row = 0; row < matches.getRowCount(); row++) {
                if ((int) matches.getValueAt(row, 0) == matchSelecionado) {
                    tabelaMatches.setRowSelectionInterval(tabelaMatches.convertRowIndexToView(row),
                            tabelaMatches.convertRowIndexToView(row));
                    carregarLog(matchSelecionado);
                    restored = true;
                    break;
                }
            }
            if (!restored) log.setRowCount(0);
        } catch (SQLException error) { mostrarErro(error); }
    }

    private interface Action { void run() throws SQLException; }
    private void executar(Action action) {
        try { action.run(); recarregar(); }
        catch (SQLException | IllegalArgumentException error) { mostrarErro(error); }
    }
    private void confirmar(String message, Action action) {
        if (JOptionPane.showConfirmDialog(this, message, "Confirmar exclusão", JOptionPane.YES_NO_OPTION)
                == JOptionPane.YES_OPTION) executar(action);
    }
    private static int number(JTextField field) {
        try { return Integer.parseInt(field.getText().trim()); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Informe IDs numéricos válidos."); }
    }
    private static int selected(JTable table, DefaultTableModel model) {
        if (table.getSelectedRow() < 0) throw new IllegalArgumentException("Selecione uma linha da lista.");
        return (int) model.getValueAt(table.convertRowIndexToModel(table.getSelectedRow()), 0);
    }
    private void info(String message) { JOptionPane.showMessageDialog(this, message); }
    private void mostrarErro(Exception error) {
        String message;
        if (error instanceof SQLException sql) {
            System.err.println("Falha no CRUD Swipe/Match: SQLState=" + sql.getSQLState()
                    + ", codigo=" + sql.getErrorCode() + ", detalhe=" + sql.getMessage());
            message = "23000".equals(sql.getSQLState()) ? "Usuário inexistente ou registro relacionado inválido."
                    : sql.getErrorCode() == 3819 ? "Valor fora das opções permitidas pelo banco."
                    : "Falha ao acessar o banco. Confira a conexão e tente novamente.";
        } else message = error.getMessage();
        JOptionPane.showMessageDialog(this, message, "Não foi possível concluir", JOptionPane.ERROR_MESSAGE);
    }
    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }
}
