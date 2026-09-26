import java.util.List;

public class Vaga {
    private long id;
    private final long idGrupoMusical;
    private final String nomeGrupo;
    private final String situacao;
    private final String descricao;
    private final String nivelMinimo;
    private final String funcaoInstrumento;
    private final List<RequisitoVaga> requisitos;

    public Vaga(long id, long idGrupoMusical, String nomeGrupo, String situacao,
                String descricao, String nivelMinimo, String funcaoInstrumento,
                List<RequisitoVaga> requisitos) {
        this.id = id;
        this.idGrupoMusical = idGrupoMusical;
        this.nomeGrupo = nomeGrupo;
        this.situacao = situacao;
        this.descricao = descricao;
        this.nivelMinimo = nivelMinimo;
        this.funcaoInstrumento = funcaoInstrumento;
        this.requisitos = List.copyOf(requisitos);
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getIdGrupoMusical() { return idGrupoMusical; }
    public String getNomeGrupo() { return nomeGrupo; }
    public String getSituacao() { return situacao; }
    public String getDescricao() { return descricao; }
    public String getNivelMinimo() { return nivelMinimo; }
    public String getFuncaoInstrumento() { return funcaoInstrumento; }
    public List<RequisitoVaga> getRequisitos() { return requisitos; }
}
