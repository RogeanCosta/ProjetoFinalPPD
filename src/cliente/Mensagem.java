package cliente;

public class Mensagem {
    private final String autor;
    private final String conteudo;

    public Mensagem(String autor, String conteudo) {
        this.autor = autor;
        this.conteudo = conteudo;
    }

    public String getAutor() {
        return autor;
    }

    public String getConteudo() {
        return conteudo;
    }
}
