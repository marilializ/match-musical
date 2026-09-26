import java.time.LocalDate;
public class GrupoMusical extends Usuario {
    private String nomedoGrupo;
    private LocalDate datadeCriacao;
    private String descricao;
    private String situacao;
    private String cidadedeAtuacao;

    public GrupoMusical(long id, String senha, String email, String nomedeUsuario, String nomedoGrupo, LocalDate datadeCriacao, String descricao, String situacao, String cidadedeAtuacao) {
        super(nomedeUsuario, senha, email, id);
        this.nomedoGrupo = nomedoGrupo;
        this.datadeCriacao = datadeCriacao;
        this.descricao = descricao;
        this.situacao = situacao;
        this.cidadedeAtuacao = cidadedeAtuacao;
    }

    public String getNomedoGrupo() {
        return nomedoGrupo;
    }

    public void setNomedoGrupo(String nomedoGrupo) {
        this.nomedoGrupo = nomedoGrupo;
    }

    public LocalDate getDatadeCriacao() {
        return datadeCriacao;
    }

    public void setDatadeCriacao(LocalDate datadeCriacao) {
        this.datadeCriacao = datadeCriacao;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getSituacao() {
        return situacao;
    }

    public void setSituacao(String situacao) {
        this.situacao = situacao;
    }

    public String getCidadedeAtuacao() {
        return cidadedeAtuacao;
    }

    public void setCidadedeAtuacao(String cidadedeAtuacao) {
        this.cidadedeAtuacao = cidadedeAtuacao;
    }
}

