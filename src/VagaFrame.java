import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

public class VagaFrame extends JFrame {
    private final VagaDAO dao = new VagaDAO();
    private final JComboBox<VagaDAO.GrupoOpcao> grupo = new JComboBox<>();
    private final JComboBox<String> situacao = new JComboBox<>(new String[] {"Aberta", "Fechada"});
    private final JComboBox<String> nivel = new JComboBox<>(new String[] {
            "", "Iniciante", "Basico", "Intermediario", "Avancado", "Profissional"
    });
    private final JTextField funcao = new JTextField();
    private final JTextArea descricao = new JTextArea(3, 20);
    private final JTextArea requisitos = new JTextArea(4, 20);
    private final DefaultTableModel modelo = new DefaultTableModel(
            new String[] {"ID", "Grupo", "Situacao", "Funcao", "Nivel minimo", "Requisitos"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable tabela = new JTable(modelo);
    private List<Vaga> vagas = new ArrayList<>();
    private long idSelecionado;

    public VagaFrame() {
        super("SYNC - Vagas");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JPanel campos = new JPanel(new GridLayout(0, 2, 8, 5));
        adicionarCampo(campos, "Grupo musical *", grupo);
        adicionarCampo(campos, "Situacao", situacao);
        adicionarCampo(campos, "Funcao / instrumento", funcao);
        adicionarCampo(campos, "Nivel minimo", nivel);
        adicionarCampo(campos, "Descricao", new JScrollPane(descricao));
        adicionarCampo(campos, "Requisitos (um por linha)", new JScrollPane(requisitos));

        JButton novo = new JButton("Limpar / nova");
        JButton cadastrar = new JButton("Cadastrar");
        JButton atualizar = new JButton("Atualizar selecionada");
        JButton excluir = new JButton("Excluir selecionada");
        JButton recarregar = new JButton("Recarregar");
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.LEFT));
        botoes.add(novo);
        botoes.add(cadastrar);
        botoes.add(atualizar);
        botoes.add(excluir);
        botoes.add(recarregar);

        JPanel topo = new JPanel(new BorderLayout());
        topo.add(campos, BorderLayout.CENTER);
        topo.add(botoes, BorderLayout.SOUTH);
        add(topo, BorderLayout.NORTH);

        tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabela.setAutoCreateRowSorter(true);
        tabela.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                preencherSelecionada();
            }
        });
        add(new JScrollPane(tabela), BorderLayout.CENTER);

        novo.addActionListener(event -> limpar());
        cadastrar.addActionListener(event -> cadastrar());
        atualizar.addActionListener(event -> atualizar());
        excluir.addActionListener(event -> excluir());
        recarregar.addActionListener(event -> carregar());
        carregar();
    }

    private static void adicionarCampo(JPanel painel, String rotulo, java.awt.Component componente) {
        painel.add(new JLabel(rotulo));
        painel.add(componente);
    }

    private void carregar() {
        try {
            List<VagaDAO.GrupoOpcao> grupos = dao.listarGrupos();
            vagas = dao.listar();
            grupo.removeAllItems();
            for (VagaDAO.GrupoOpcao opcao : grupos) {
                grupo.addItem(opcao);
            }
            modelo.setRowCount(0);
            for (Vaga vaga : vagas) {
                modelo.addRow(new Object[] {
                        vaga.getId(), vaga.getNomeGrupo(), vaga.getSituacao(),
                        vaga.getFuncaoInstrumento(), vaga.getNivelMinimo(),
                        vaga.getRequisitos().size()
                });
            }
            limpar();
        } catch (SQLException error) {
            mostrarErro(error);
        }
    }

    private void preencherSelecionada() {
        int linha = tabela.getSelectedRow();
        if (linha < 0) {
            return;
        }
        Vaga vaga = vagas.get(tabela.convertRowIndexToModel(linha));
        idSelecionado = vaga.getId();
        for (int indice = 0; indice < grupo.getItemCount(); indice++) {
            if (grupo.getItemAt(indice).id() == vaga.getIdGrupoMusical()) {
                grupo.setSelectedIndex(indice);
                break;
            }
        }
        situacao.setSelectedItem(vaga.getSituacao());
        nivel.setSelectedItem(Objects.toString(vaga.getNivelMinimo(), ""));
        funcao.setText(Objects.toString(vaga.getFuncaoInstrumento(), ""));
        descricao.setText(Objects.toString(vaga.getDescricao(), ""));
        requisitos.setText(vaga.getRequisitos().stream()
                .map(RequisitoVaga::descricao)
                .reduce((a, b) -> a + "\n" + b).orElse(""));
    }

    private void limpar() {
        idSelecionado = 0;
        tabela.clearSelection();
        if (grupo.getItemCount() > 0) {
            grupo.setSelectedIndex(0);
        }
        situacao.setSelectedItem("Aberta");
        nivel.setSelectedIndex(0);
        funcao.setText("");
        descricao.setText("");
        requisitos.setText("");
    }

    private Vaga lerFormulario() {
        VagaDAO.GrupoOpcao grupoSelecionado = (VagaDAO.GrupoOpcao) grupo.getSelectedItem();
        if (grupoSelecionado == null) {
            throw new IllegalArgumentException("Nao ha grupo musical cadastrado para criar uma vaga.");
        }
        List<RequisitoVaga> itens = new ArrayList<>();
        for (String linha : requisitos.getText().split("\\R")) {
            String texto = linha.trim();
            if (!texto.isEmpty()) {
                itens.add(new RequisitoVaga(itens.size() + 1, texto));
            }
        }
        return new Vaga(idSelecionado, grupoSelecionado.id(), grupoSelecionado.nome(),
                (String) situacao.getSelectedItem(), vazioParaNulo(descricao.getText()),
                vazioParaNulo((String) nivel.getSelectedItem()),
                vazioParaNulo(funcao.getText()), itens);
    }

    private static String vazioParaNulo(String texto) {
        String valor = texto == null ? "" : texto.trim();
        return valor.isEmpty() ? null : valor;
    }

    private void cadastrar() {
        try {
            dao.inserir(lerFormulario());
            carregar();
            JOptionPane.showMessageDialog(this, "Vaga cadastrada.");
        } catch (IllegalArgumentException | SQLException error) {
            mostrarErro(error);
        }
    }

    private void atualizar() {
        if (idSelecionado == 0) {
            JOptionPane.showMessageDialog(this, "Selecione uma vaga na tabela.");
            return;
        }
        try {
            if (!dao.atualizar(lerFormulario())) {
                JOptionPane.showMessageDialog(this, "Vaga nao encontrada. Recarregue a lista.");
                return;
            }
            carregar();
            JOptionPane.showMessageDialog(this, "Vaga atualizada.");
        } catch (IllegalArgumentException | SQLException error) {
            mostrarErro(error);
        }
    }

    private void excluir() {
        if (idSelecionado == 0) {
            JOptionPane.showMessageDialog(this, "Selecione uma vaga na tabela.");
            return;
        }
        int resposta = JOptionPane.showConfirmDialog(this,
                "Excluir esta vaga e seus requisitos?", "Confirmar exclusao",
                JOptionPane.YES_NO_OPTION);
        if (resposta != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            if (dao.excluir(idSelecionado)) {
                carregar();
                JOptionPane.showMessageDialog(this, "Vaga excluida.");
            } else {
                JOptionPane.showMessageDialog(this, "Vaga nao encontrada. Recarregue a lista.");
            }
        } catch (SQLException error) {
            mostrarErro(error);
        }
    }

    private void mostrarErro(Exception error) {
        String mensagem = error.getMessage();
        if (error instanceof SQLException sqlError) {
            if (sqlError.getErrorCode() == 1452) {
                mensagem = "O grupo selecionado nao existe mais. Recarregue a lista.";
            } else if (sqlError.getErrorCode() == 3819) {
                mensagem = "Situacao ou nivel minimo invalido.";
            }
        }
        JOptionPane.showMessageDialog(this, mensagem, "Erro", JOptionPane.ERROR_MESSAGE);
    }
}
