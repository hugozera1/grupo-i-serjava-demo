package br.edu.fatecfranca.ecommerce.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Representa um produto do catálogo do e-commerce.
 * Os dados do registro (entidade, referências às imagens, flag de capa e datas)
 * são armazenados no banco de dados local SQLite (ecommerce.db), enquanto os
 * arquivos binários das imagens ficam no Google Cloud Storage (GCS).
 */
public class Produto {

    private String id;
    private String nome;
    private String descricao;
    private BigDecimal preco;
    private String categoria;
    private LocalDateTime dataCadastro;
    private List<ProdutoImagem> imagens = new ArrayList<>();

    public Produto() {
    }

    public Produto(String id, String nome, String descricao, BigDecimal preco, String categoria) {
        this.id = id;
        this.nome = nome;
        this.descricao = descricao;
        this.preco = preco;
        this.categoria = categoria;
        this.dataCadastro = LocalDateTime.now();
        this.imagens = new ArrayList<>();
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

    public LocalDateTime getDataCadastro() {
        return dataCadastro;
    }

    public void setDataCadastro(LocalDateTime dataCadastro) {
        this.dataCadastro = dataCadastro;
    }

    public List<ProdutoImagem> getImagens() {
        if (imagens == null) {
            imagens = new ArrayList<>();
        }
        return imagens;
    }

    public void setImagens(List<ProdutoImagem> imagens) {
        this.imagens = (imagens != null) ? new ArrayList<>(imagens) : new ArrayList<>();
    }

    /**
     * Retorna a imagem principal (capa do catálogo) ou, se nenhuma estiver explicitamente
     * marcada como principal, retorna a primeira imagem da lista caso exista.
     */
    @JsonIgnore
    public Optional<ProdutoImagem> obterImagemPrincipal() {
        if (imagens == null || imagens.isEmpty()) {
            return Optional.empty();
        }
        return imagens.stream()
                .filter(ProdutoImagem::isEhPrincipal)
                .findFirst()
                .or(() -> Optional.of(imagens.get(0)));
    }

    /**
     * URL pública da imagem principal/capa para fácil consumo pelo frontend na vitrine.
     */
    @JsonProperty("urlImagemPrincipal")
    public String getUrlImagemPrincipal() {
        return obterImagemPrincipal().map(ProdutoImagem::getUrlPublica).orElse(null);
    }

    /**
     * Caminho no GCS da imagem principal.
     */
    @JsonProperty("caminhoImagemPrincipal")
    public String getCaminhoImagemPrincipal() {
        return obterImagemPrincipal().map(ProdutoImagem::getCaminhoObjeto).orElse(null);
    }

    @JsonProperty("totalImagens")
    public int getTotalImagens() {
        return imagens != null ? imagens.size() : 0;
    }

    @Override
    public String toString() {
        String capa = obterImagemPrincipal()
                .map(img -> img.getCaminhoObjeto() + " (" + img.getUrlPublica() + ")")
                .orElse("(nenhuma imagem cadastrada)");

        return String.format(
                """
                ------------------------------------------------------------
                ID do Produto       : %s
                Nome                : %s
                Categoria           : %s
                Preço               : R$ %s
                Descrição           : %s
                Data de Cadastro    : %s
                Armazenamento Dados : Banco de Dados Local SQLite (ecommerce.db)
                Total de Imagens    : %d
                Imagem Capa (GCS)   : %s
                ------------------------------------------------------------""",
                id,
                nome,
                categoria,
                preco != null ? preco.toPlainString() : "0.00",
                descricao,
                dataCadastro != null ? dataCadastro.toString() : "N/A",
                getTotalImagens(),
                capa
        );
    }
}
