package br.edu.fatecfranca.ecommerce.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Representa um produto do catálogo do e-commerce.
 * Seus dados são serializados em JSON e armazenados no bucket como "produtos/produto-XXX.json".
 */
public class Produto {

    private String id;
    private String nome;
    private String descricao;
    private BigDecimal preco;
    private String categoria;
    private String caminhoObjetoJson;
    private String urlPublicaJson;
    private String caminhoObjetoImagem;
    private String urlPublicaImagem;
    private LocalDateTime dataCadastro;

    // Construtor padrão exigido pelo Jackson para desserialização JSON
    public Produto() {
    }

    public Produto(String id, String nome, String descricao, BigDecimal preco, String categoria) {
        this.id = id;
        this.nome = nome;
        this.descricao = descricao;
        this.preco = preco;
        this.categoria = categoria;
        this.dataCadastro = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        this.preco = preco;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getCaminhoObjetoJson() {
        return caminhoObjetoJson;
    }

    public void setCaminhoObjetoJson(String caminhoObjetoJson) {
        this.caminhoObjetoJson = caminhoObjetoJson;
    }

    public String getUrlPublicaJson() {
        return urlPublicaJson;
    }

    public void setUrlPublicaJson(String urlPublicaJson) {
        this.urlPublicaJson = urlPublicaJson;
    }

    public String getCaminhoObjetoImagem() {
        return caminhoObjetoImagem;
    }

    public void setCaminhoObjetoImagem(String caminhoObjetoImagem) {
        this.caminhoObjetoImagem = caminhoObjetoImagem;
    }

    public String getUrlPublicaImagem() {
        return urlPublicaImagem;
    }

    public void setUrlPublicaImagem(String urlPublicaImagem) {
        this.urlPublicaImagem = urlPublicaImagem;
    }

    public LocalDateTime getDataCadastro() {
        return dataCadastro;
    }

    public void setDataCadastro(LocalDateTime dataCadastro) {
        this.dataCadastro = dataCadastro;
    }

    @Override
    public String toString() {
        return String.format(
                """
                ------------------------------------------------------------
                ID do Produto       : %s
                Nome                : %s
                Categoria           : %s
                Preço               : R$ %s
                Descrição           : %s
                Data de Cadastro    : %s
                Objeto JSON (GCS)   : %s
                URL Pública JSON    : %s
                Objeto Imagem (GCS) : %s
                URL Pública Imagem  : %s
                ------------------------------------------------------------""",
                id,
                nome,
                categoria,
                preco != null ? preco.toPlainString() : "0.00",
                descricao,
                dataCadastro != null ? dataCadastro.toString() : "N/A",
                caminhoObjetoJson != null ? caminhoObjetoJson : "(não definido)",
                urlPublicaJson != null ? urlPublicaJson : "(não definido)",
                caminhoObjetoImagem != null ? caminhoObjetoImagem : "(sem imagem associada)",
                urlPublicaImagem != null ? urlPublicaImagem : "(sem imagem associada)"
        );
    }
}
