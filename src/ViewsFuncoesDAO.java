import java.sql.SQLException;
import java.util.List;

/**
 * Etapa 6, tarefa 4: views da Etapa 4 e funcoes da Etapa 5 na interface.
 * Todo acesso e SQL explicito por PreparedStatement (sem ORM). As views nao
 * recebem parametro: os filtros da tela vao no WHERE do SELECT sobre elas,
 * sempre com "?".
 */
public class ViewsFuncoesDAO extends BaseDAO {

    /** Uma vaga recomendada para o musico, vinda de vw_recomendacao_vagas. */
    public record VagaRecomendada(int idVaga, int idGrupo, String grupo, String cidade,
                                  String funcaoInstrumento, String nivelMinimo,
                                  String generosEmComum, int qtdGenerosEmComum,
                                  String compatibilidade) {
    }

    /** Integrante ativo de um grupo, vindo de vw_integrantes_grupo. */
    public record Integrante(int idMusico, String musico, String funcao, String dataEntrada) {
    }

    /** Indicador da tela de vagas: quantas vagas abertas o musico atende. */
    public record IndicadorCompatibilidade(int vagasAbertas, int compativeis, int recomendadas) {
    }

    // =====================================================
    // CONSULTA: objeto da Etapa 4 ou 5 instalado no banco
    // Conceito: leitura do dicionario de dados (information_schema)
    // Pergunta que responde: "Esta view/funcao existe no banco
    // atual?" A tela usa a resposta para avisar na propria
    // secao, em vez de cair inteira quando falta um objeto.
    // =====================================================
    private static final String SQL_OBJETO_EXISTE = """
            SELECT 1 FROM information_schema.VIEWS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
            UNION ALL
            SELECT 1 FROM information_schema.ROUTINES
             WHERE ROUTINE_SCHEMA = DATABASE() AND ROUTINE_NAME = ?
            """;

    public boolean objetoExiste(String nome) throws SQLException {
        return !query(SQL_OBJETO_EXISTE, statement -> {
            statement.setString(1, nome);
            statement.setString(2, nome);
        }, result -> 1).isEmpty();
    }

    // =====================================================
    // CONSULTA: vagas recomendadas para o musico
    // Conceito: SELECT sobre view (vw_recomendacao_vagas) com
    //           filtros opcionais por parametro + funcao escalar
    //           (fn_compatibilidade_vaga) no SELECT
    // Pergunta que responde: "Quais vagas abertas combinam com
    // os generos de interesse e o nivel deste musico?"
    // Filtros opcionais: o "? IS NULL OR ..." desliga o filtro
    // quando o campo da tela vem vazio. A view nao traz o id do
    // grupo nem a cidade, por isso o JOIN com Vaga e Grupo_Musical.
    // =====================================================
    private static final String SQL_VAGAS_RECOMENDADAS = """
            SELECT r.id_vaga,
                   v.id_grupo_musical,
                   r.nome_grupo,
                   g.cidade_atuacao,
                   r.funcao_instrumento,
                   r.nivel_minimo,
                   r.generos_em_comum,
                   r.qtd_generos_em_comum,
                   fn_compatibilidade_vaga(r.id_musico, r.id_vaga) AS compatibilidade
              FROM vw_recomendacao_vagas r
              JOIN Vaga v          ON v.id_vaga = r.id_vaga
              JOIN Grupo_Musical g ON g.id_usuario = v.id_grupo_musical
             WHERE r.id_musico = ?
               AND (? IS NULL OR r.funcao_instrumento LIKE ? ESCAPE '!')
               AND (? IS NULL OR r.nivel_minimo = ?)
             ORDER BY r.qtd_generos_em_comum DESC, r.id_vaga
            """;

    /**
     * @param funcao      trecho da funcao/instrumento, ou null para nao filtrar
     * @param nivelMinimo nivel minimo exato da vaga, ou null para nao filtrar
     */
    public List<VagaRecomendada> vagasRecomendadas(int idMusico, String funcao, String nivelMinimo)
            throws SQLException {
        String padrao = funcao == null ? null : "%" + escaparLike(funcao) + "%";
        return query(SQL_VAGAS_RECOMENDADAS, statement -> {
            statement.setInt(1, idMusico);
            statement.setString(2, funcao);
            statement.setString(3, padrao);
            statement.setString(4, nivelMinimo);
            statement.setString(5, nivelMinimo);
        }, result -> new VagaRecomendada(
                result.getInt("id_vaga"),
                result.getInt("id_grupo_musical"),
                result.getString("nome_grupo"),
                result.getString("cidade_atuacao"),
                result.getString("funcao_instrumento"),
                result.getString("nivel_minimo"),
                result.getString("generos_em_comum"),
                result.getInt("qtd_generos_em_comum"),
                result.getString("compatibilidade")));
    }

    // =====================================================
    // CONSULTA: indicador de compatibilidade do musico
    // Conceito: agregacao condicional (SUM de CASE) sobre o
    //           resultado de uma funcao + subconsulta na view
    // Pergunta que responde: "De todas as vagas abertas,
    // quantas o musico atende em nivel, e quantas a view
    // recomenda (nivel + genero em comum)?"
    // Compara com o texto exato devolvido pela funcao.
    // =====================================================
    private static final String SQL_INDICADOR_COMPATIBILIDADE = """
            SELECT COUNT(*) AS vagas_abertas,
                   COALESCE(SUM(CASE WHEN fn_compatibilidade_vaga(?, v.id_vaga) = 'Compatível'
                                     THEN 1 ELSE 0 END), 0) AS compativeis,
                   (SELECT COUNT(*) FROM vw_recomendacao_vagas r
                     WHERE r.id_musico = ?) AS recomendadas
              FROM Vaga v
             WHERE v.situacao = 'Aberta'
            """;

    public IndicadorCompatibilidade indicadorCompatibilidade(int idMusico) throws SQLException {
        return query(SQL_INDICADOR_COMPATIBILIDADE, statement -> {
            statement.setInt(1, idMusico);
            statement.setInt(2, idMusico);
        }, result -> new IndicadorCompatibilidade(
                result.getInt("vagas_abertas"),
                result.getInt("compativeis"),
                result.getInt("recomendadas"))).get(0);
    }

    // =====================================================
    // CONSULTA: integrantes ativos de um grupo
    // Conceito: SELECT sobre view (vw_integrantes_grupo) com
    //           filtro por parametro
    // Pergunta que responde: "Quem toca hoje neste grupo, em
    // qual funcao e desde quando?"
    // =====================================================
    private static final String SQL_INTEGRANTES_GRUPO = """
            SELECT id_musico,
                   musico,
                   funcao,
                   DATE_FORMAT(data_entrada, '%d/%m/%Y') AS data_entrada
              FROM vw_integrantes_grupo
             WHERE id_grupo = ?
             ORDER BY vw_integrantes_grupo.data_entrada, musico
            """;

    public List<Integrante> integrantesDoGrupo(int idGrupo) throws SQLException {
        return query(SQL_INTEGRANTES_GRUPO, statement -> statement.setInt(1, idGrupo),
                result -> new Integrante(
                        result.getInt("id_musico"),
                        result.getString("musico"),
                        result.getString("funcao"),
                        result.getString("data_entrada")));
    }

    // =====================================================
    // CONSULTA: taxa de retribuicao do usuario
    // Conceito: chamada de funcao armazenada (SELECT fn(?))
    // Pergunta que responde: "Que percentual dos likes deste
    // usuario recebeu like de volta?" A regra fica so na
    // funcao fn_taxa_retribuicao_usuario (Etapa 5), sem copia
    // em Java.
    // =====================================================
    private static final String SQL_TAXA_RETRIBUICAO = "SELECT fn_taxa_retribuicao_usuario(?) AS taxa";

    public double taxaRetribuicao(int idUsuario) throws SQLException {
        return query(SQL_TAXA_RETRIBUICAO, statement -> statement.setInt(1, idUsuario),
                result -> result.getDouble("taxa")).get(0);
    }

    /** Escapa % e _ para que o texto digitado seja procurado literalmente no LIKE. */
    private static String escaparLike(String texto) {
        return texto.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
