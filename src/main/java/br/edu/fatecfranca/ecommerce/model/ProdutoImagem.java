package br.edu.fatecfranca.ecommerce.model;

import java.time.LocalDateTime;

/**
 * Representa uma imagem vinculada a um produto do catálogo.
 * O arquivo binário correspondente fica armazenado exclusivamente no bucket do GCS
 * (ex: imagens/produto-001/nome.png), enquanto seus metadados e o flag de capa
 * ficam registrados no banco de dados local SQLite.
 */
public class ProdutoImagem {

    private Long id;
    private String produtoId;
    private String caminhoObjeto;
    private String urlPublica;
    private String nomeArquivoOriginal;
    private boolean ehPrincipal;
    private LocalDateTime dataUpload;

    public ProdutoImagem() {
    }

    public ProdutoImagem(Long id, String produtoId, String caminhoObjeto, String urlPublica,
                         String nomeArquivoOriginal, boolean ehPrincipal, LocalDateTime dataUpload) {
        this.id = id;
        this.produtoId = produtoId;
        this.caminhoObjeto = caminhoObjeto;
        this.urlPublica = urlPublica;
        this.nomeArquivoOriginal = nomeArquivoOriginal;
        this.ehPrincipal = ehPrincipal;
        this.dataUpload = dataUpload != null ? dataUpload : LocalDateTime.now();
    }

    public ProdutoImagem(String produtoId, String caminhoObjeto, String urlPublica,
                         String nomeArquivoOriginal, boolean ehPrincipal) {
        this(null, produtoId, caminhoObjeto, urlPublica, nomeArquivoOriginal, ehPrincipal, LocalDateTime.now());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProdutoId() {
        return produtoId;
    }

    public void setProdutoId(String produtoId) {
        this.produtoId = produtoId;
    }

    public String getCaminhoObjeto() {
        return caminhoObjeto;
    }

    public void setCaminhoObjeto(String caminhoObjeto) {
        this.caminhoObjeto = caminhoObjeto;
    }

    public String getUrlPublica() {
        return urlPublica;
    }

    public void setUrlPublica(String urlPublica) {
        this.urlPublica = urlPublica;
    }

    public String getNomeArquivoOriginal() {
        return nomeArquivoOriginal;
    }

    public void setNomeArquivoOriginal(String nomeArquivoOriginal) {
        this.nomeArquivoOriginal = nomeArquivoOriginal;
    }

    public boolean isEhPrincipal() {
        return ehPrincipal;
    }

    public void setEhPrincipal(boolean ehPrincipal) {
        this.ehPrincipal = ehPrincipal;
    }

    public LocalDateTime getDataUpload() {
        return dataUpload;
    }

    public void setDataUpload(LocalDateTime dataUpload) {
        this.dataUpload = dataUpload;
    }

    @Override
    public String toString() {
        return String.format(
                "Imagem [id=%d, principal=%s, arquivo='%s', gcs='%s']",
                id, ehPrincipal ? "SIM (Capa)" : "Não", nomeArquivoOriginal, caminhoObjeto
        );
    }
}
