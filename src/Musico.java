import java.time.LocalDate;
import java.time.LocalDateTime;

public class Musico extends Usuario{
    private LocalDateTime disponibilidade;
    private String nome;
    private String nomeArtistico;
    private String biografia;
    private String categoriaPrincipal;
    private LocalDate datadeNascimento;
    private String niveldeExperiencia;

    public Musico (long id, String senha, String email, String nomedeUsuario, LocalDateTime disponibilidade, String nome, String nomeArtistico, String biografia, String categoriaPrincipal, LocalDate datadeNascimento, String niveldeExperiencia){
        super (nomedeUsuario, senha, email, id);
        this.disponibilidade=disponibilidade;
        this.nome=nome;
        this.nomeArtistico=nomeArtistico;
        this.biografia=biografia;
        this.categoriaPrincipal=categoriaPrincipal;
        this.datadeNascimento=datadeNascimento;
        this.niveldeExperiencia=niveldeExperiencia;
    }

    public LocalDateTime getDisponibilidade() {
        return disponibilidade;
    }

    public void setDisponibilidade(LocalDateTime disponibilidade) {
        this.disponibilidade = disponibilidade;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getNomeArtistico() {
        return nomeArtistico;
    }

    public void setNomeArtistico(String nomeArtistico) {
        this.nomeArtistico = nomeArtistico;

    }

    public String getBiografia() {
        return biografia;
    }

    public void setBiografia(String biografia) {
        this.biografia = biografia;
    }

    public LocalDate getDatadeNascimento() {
        return datadeNascimento;
    }

    public void setDatadeNascimento(LocalDate datadeNascimento) {
        this.datadeNascimento = datadeNascimento;
    }

    public String getCategoriaPrincipal() {
        return categoriaPrincipal;
    }

    public void setCategoriaPrincipal(String categoriaPrincipal){
        this.categoriaPrincipal=categoriaPrincipal;
    }

    public String getNiveldeExperiencia() {
        return niveldeExperiencia;
    }

    public void setNiveldeExperiencia(String niveldeExperiencia){
        this.niveldeExperiencia=niveldeExperiencia;
    }
}
