import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

public class MusicoFrame extends JFrame {
    private final MusicoDAO dao = new MusicoDAO();
    private final JTextField usuario = new JTextField();
    private final JTextField email = new JTextField();
    private final JPasswordField senha = new JPasswordField();
    private final JTextField nome = new JTextField();
    private final JTextField nascimento = new JTextField();
    private final JTextField nomeArtistico = new JTextField();
    private final JComboBox<String> nivel = new JComboBox<>(new String[] {
            "", "Iniciante", "Basico", "Intermediario", "Avancado", "Profissional"
    });
    private final JTextField disponibilidade = new JTextField();
    private final JTextField categoria = new JTextField();
    private final JTextArea biografia = new JTextArea(3, 20);
    private final DefaultTableModel modelo = new DefaultTableModel(
            new String[] {"ID", "Usuario", "Nome", "E-mail", "Nome artistico", "Nivel", "Categoria"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable tabela = new JTable(modelo);
    private List<Musico> musicos = new ArrayList<>();
    private long idSelecionado;

    public MusicoFrame() {
        super("SYNC - Musicos");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JPanel campos = new JPanel(new GridLayout(0, 2, 8, 5));
        adicionarCampo(campos, "Usuario *", usuario);
        adicionarCampo(campos, "E-mail *", email);
        adicionarCampo(campos, "Senha (obrigatoria no cadastro)", senha);
        adicionarCampo(campos, "Nome *", nome);
        adicionarCampo(campos, "Nascimento (AAAA-MM-DD)", nascimento);
        adicionarCampo(campos, "Nome artistico", nomeArtistico);
        adicionarCampo(campos, "Nivel", nivel);
        adicionarCampo(campos, "Disponibilidade", disponibilidade);
        adicionarCampo(campos, "Categoria principal", categoria);
        adicionarCampo(campos, "Biografia", new JScrollPane(biografia));

        JButton novo = new JButton("Limpar / novo");
        JButton cadastrar = new JButton("Cadastrar");
        JButton atualizar = new JButton("Atualizar selecionado");
        JButton excluir = new JButton("Excluir selecionado");
        JButton recarregar = new JButton("Recarregar");
        JButton abrirVagas = new JButton("Vagas");
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.LEFT));
        botoes.add(novo);
        botoes.add(cadastrar);
        botoes.add(atualizar);
        botoes.add(excluir);
        botoes.add(recarregar);
        botoes.add(abrirVagas);

        JPanel topo = new JPanel(new BorderLayout());
        topo.add(campos, BorderLayout.CENTER);
        topo.add(botoes, BorderLayout.SOUTH);
        add(topo, BorderLayout.NORTH);

        tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabela.setAutoCreateRowSorter(true);
        tabela.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                preencherSelecionado();
            }
        });
        add(new JScrollPane(tabela), BorderLayout.CENTER);

        novo.addActionListener(event -> limpar());
        cadastrar.addActionListener(event -> cadastrar());
        atualizar.addActionListener(event -> atualizar());
        excluir.addActionListener(event -> excluir());
        recarregar.addActionListener(event -> carregar());
        abrirVagas.addActionListener(event -> new VagaFrame().setVisible(true));
        carregar();
    }

    private static void adicionarCampo(JPanel painel, String rotulo, java.awt.Component componente) {
        painel.add(new JLabel(rotulo));
        painel.add(componente);
    }

    private void carregar() {
        try {
            musicos = dao.listar();
            modelo.setRowCount(0);
            for (Musico musico : musicos) {
                modelo.addRow(new Object[] {
                        musico.getID(), musico.getNomedeUsuario(), musico.getNome(),
                        musico.getEmail(), musico.getNomeArtistico(),
                        musico.getNiveldeExperiencia(), musico.getCategoriaPrincipal()
                });
            }
            limpar();
        } catch (SQLException error) {
            mostrarErro(error);
        }
    }

    private void preencherSelecionado() {
        int linha = tabela.getSelectedRow();
        if (linha < 0) {
            return;
        }
        Musico musico = musicos.get(tabela.convertRowIndexToModel(linha));
        idSelecionado = musico.getID();
        usuario.setText(musico.getNomedeUsuario());
        email.setText(musico.getEmail());
        senha.setText("");
        nome.setText(musico.getNome());
        nascimento.setText(musico.getDatadeNascimento() == null
                ? "" : musico.getDatadeNascimento().toString());
        nomeArtistico.setText(valor(musico.getNomeArtistico()));
        nivel.setSelectedItem(valor(musico.getNiveldeExperiencia()));
        disponibilidade.setText(valor(musico.getDisponibilidade()));
        categoria.setText(valor(musico.getCategoriaPrincipal()));
        biografia.setText(valor(musico.getBiografia()));
    }

    private static String valor(String texto) {
        return texto == null ? "" : texto;
    }

    private void limpar() {
        idSelecionado = 0;
        tabela.clearSelection();
        usuario.setText("");
        email.setText("");
        senha.setText("");
        nome.setText("");
        nascimento.setText("");
        nomeArtistico.setText("");
        nivel.setSelectedIndex(0);
        disponibilidade.setText("");
        categoria.setText("");
        biografia.setText("");
    }

    private Musico lerFormulario(boolean novo) {
        String nomeUsuario = usuario.getText().trim();
        String emailDigitado = email.getText().trim();
        String nomeDigitado = nome.getText().trim();
        char[] caracteres = senha.getPassword();
        String senhaDigitada = new String(caracteres);
        Arrays.fill(caracteres, '\0');

        if (nomeUsuario.isEmpty() || emailDigitado.isEmpty() || nomeDigitado.isEmpty()) {
            throw new IllegalArgumentException("Usuario, e-mail e nome sao obrigatorios.");
        }
        if (novo && senhaDigitada.isBlank()) {
            throw new IllegalArgumentException("Informe uma senha para o cadastro.");
        }

        LocalDate data = null;
        if (!nascimento.getText().isBlank()) {
            try {
                data = LocalDate.parse(nascimento.getText().trim());
            } catch (DateTimeParseException error) {
                throw new IllegalArgumentException("Nascimento deve estar no formato AAAA-MM-DD.");
            }
        }
        return new Musico(idSelecionado, senhaDigitada, emailDigitado, nomeUsuario,
                vazioParaNulo(disponibilidade.getText()), nomeDigitado,
                vazioParaNulo(nomeArtistico.getText()), vazioParaNulo(biografia.getText()),
                vazioParaNulo(categoria.getText()), data,
                vazioParaNulo((String) nivel.getSelectedItem()));
    }

    private static String vazioParaNulo(String texto) {
        String valor = texto == null ? "" : texto.trim();
        return valor.isEmpty() ? null : valor;
    }

    private void cadastrar() {
        try {
            dao.inserir(lerFormulario(true));
            carregar();
            JOptionPane.showMessageDialog(this, "Musico cadastrado.");
        } catch (IllegalArgumentException | SQLException error) {
            mostrarErro(error);
        }
    }

    private void atualizar() {
        if (idSelecionado == 0) {
            JOptionPane.showMessageDialog(this, "Selecione um musico na tabela.");
            return;
        }
        try {
            if (!dao.atualizar(lerFormulario(false))) {
                JOptionPane.showMessageDialog(this, "Musico nao encontrado. Recarregue a lista.");
                return;
            }
            carregar();
            JOptionPane.showMessageDialog(this, "Musico atualizado.");
        } catch (IllegalArgumentException | SQLException error) {
            mostrarErro(error);
        }
    }

    private void excluir() {
        if (idSelecionado == 0) {
            JOptionPane.showMessageDialog(this, "Selecione um musico na tabela.");
            return;
        }
        int resposta = JOptionPane.showConfirmDialog(this,
                "Excluir este musico e seus dados relacionados?", "Confirmar exclusao",
                JOptionPane.YES_NO_OPTION);
        if (resposta != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            if (dao.excluir(idSelecionado)) {
                carregar();
                JOptionPane.showMessageDialog(this, "Musico excluido.");
            } else {
                JOptionPane.showMessageDialog(this, "Musico nao encontrado. Recarregue a lista.");
            }
        } catch (SQLException error) {
            mostrarErro(error);
        }
    }

    private void mostrarErro(Exception error) {
        String mensagem = error.getMessage();
        if (error instanceof SQLException sqlError) {
            if (sqlError.getErrorCode() == 1062) {
                mensagem = "Usuario ou e-mail ja cadastrado.";
            } else if (sqlError.getErrorCode() == 3819) {
                mensagem = "Valor invalido para um dos campos com opcoes restritas.";
            }
        }
        JOptionPane.showMessageDialog(this, mensagem, "Erro", JOptionPane.ERROR_MESSAGE);
    }
}
