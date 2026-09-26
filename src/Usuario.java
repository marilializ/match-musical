public class Usuario {
    private String nomedeUsuario;
    private String senha;
    private String email;
    private long id;

    public Usuario(String nomedeUsuario, String senha, String email, long id){
        this.nomedeUsuario=nomedeUsuario;
        this.senha=senha;
        this.email=email;
        this.id=id;
    }

    public String getNomedeUsuario() {
        return nomedeUsuario;
    }

    public void setNomedeUsuario(String nomedeUsuario) {
        this.nomedeUsuario = nomedeUsuario;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public long getID() {
        return id;
    }

    public void setID(long ID) {
        this.id = ID;
    }
}
