package br.edu.fatecfranca.ecommerce.service;

import br.edu.fatecfranca.ecommerce.model.Produto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço de domínio responsável pelas regras de negócio dos produtos do e-commerce.
 * Coordena a geração consistente de IDs (produto-001, produto-002...), serialização JSON
 * via Jackson e chamadas ao serviço de armazenamento no GCS.
 */
public class ProdutoService {

    public static final String PREFIXO_PRODUTOS = "produtos/";
    public static final String PREFIXO_IMAGENS = "imagens/";
    private static final Pattern PADRAO_ID_PRODUTO = Pattern.compile("produto-(\\d{3,})");

    private final GcsStorageService gcsService;
    private final ObjectMapper objectMapper;

    public ProdutoService(GcsStorageService gcsService) {
        this.gcsService = gcsService;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Gera o próximo identificador sequencial livre consultando os objetos já existentes no bucket.
     * Evita sobrescrever produtos já cadastrados (ex: retorna "produto-001", "produto-002", etc.).
     */
    public String gerarProximoId() {
        List<String> objetosExistentes = gcsService.listarObjetosPorPrefixo(PREFIXO_PRODUTOS);
        int maiorNumero = 0;

        for (String caminho : objetosExistentes) {
            Matcher matcher = PADRAO_ID_PRODUTO.matcher(caminho);
            if (matcher.find()) {
                try {
                    int numero = Integer.parseInt(matcher.group(1));
                    if (numero > maiorNumero) {
                        maiorNumero = numero;
                    }
                } catch (NumberFormatException ignored) {
                    // Ignora sufixos que não sejam inteiros válidos
                }
            }
        }

        int proximoNumero = maiorNumero + 1;
        String idCandidato = String.format("produto-%03d", proximoNumero);

        // Garantia extra contra colisão
        while (gcsService.objetoExiste(montarCaminhoJson(idCandidato))) {
            proximoNumero++;
            idCandidato = String.format("produto-%03d", proximoNumero);
        }

        return idCandidato;
    }

    /**
     * Cadastra um novo produto, gera seu arquivo JSON em UTF-8 e faz upload para o GCS.
     */
    public Produto cadastrarProduto(String nome, String descricao, BigDecimal preco, String categoria) {
        String id = gerarProximoId();
        String caminhoJson = montarCaminhoJson(id);
        String urlPublicaJson = gcsService.gerarUrlPublica(caminhoJson);

        Produto produto = new Produto(id, nome, descricao, preco, categoria);
        produto.setCaminhoObjetoJson(caminhoJson);
        produto.setUrlPublicaJson(urlPublicaJson);

        salvarProdutoNoBucket(produto);
        return produto;
    }

    /**
     * Realiza o upload de uma imagem local para "imagens/produto-XXX.<ext>" no bucket
     * e atualiza o JSON do produto correspondente com o caminho e a URL pública da imagem.
     */
    public Produto anexarImagemAoProduto(String idOuNumero, Path caminhoImagemLocal) {
        String idNormalizado = normalizarIdProduto(idOuNumero);
        Produto produto = buscarProdutoPorId(idNormalizado)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Produto '" + idNormalizado + "' não encontrado no bucket. Cadastre-o antes de enviar a imagem."
                ));

        String nomeArquivo = caminhoImagemLocal.getFileName().toString().toLowerCase(Locale.ROOT);
        String extensao = extrairExtensaoValida(nomeArquivo);
        String contentType = determinarContentTypeImagem(extensao);

        // Se o produto já possuía uma imagem de outra extensão, remove a antiga para não deixar lixo
        String objetoImagemAntigo = produto.getCaminhoObjetoImagem();
        String novoObjetoImagem = PREFIXO_IMAGENS + idNormalizado + "." + extensao;

        gcsService.uploadArquivo(novoObjetoImagem, caminhoImagemLocal, contentType);

        if (objetoImagemAntigo != null && !objetoImagemAntigo.equals(novoObjetoImagem)) {
            gcsService.excluirObjeto(objetoImagemAntigo);
        }

        produto.setCaminhoObjetoImagem(novoObjetoImagem);
        produto.setUrlPublicaImagem(gcsService.gerarUrlPublica(novoObjetoImagem));

        // Atualiza o arquivo JSON do produto no GCS com os metadados da imagem
        salvarProdutoNoBucket(produto);
        return produto;
    }

    /**
     * Lista todos os produtos armazenados em "produtos/*.json" no bucket do GCS,
     * ordenados pelo identificador.
     */
    public List<Produto> listarProdutos() {
        List<String> objetosJson = gcsService.listarObjetosPorPrefixo(PREFIXO_PRODUTOS);
        List<Produto> produtos = new ArrayList<>();

        for (String objeto : objetosJson) {
            if (objeto.endsWith(".json")) {
                gcsService.lerObjetoComoTexto(objeto).ifPresent(conteudoJson -> {
                    try {
                        Produto p = objectMapper.readValue(conteudoJson, Produto.class);
                        produtos.add(p);
                    } catch (JsonProcessingException e) {
                        System.err.println("[AVISO] Arquivo JSON inválido ignorado (" + objeto + "): " + e.getMessage());
                    }
                });
            }
        }

        produtos.sort(Comparator.comparing(p -> p.getId() != null ? p.getId() : ""));
        return produtos;
    }

    /**
     * Consulta um produto específico pelo seu ID (ex: "produto-001" ou apenas "1").
     */
    public Optional<Produto> buscarProdutoPorId(String idOuNumero) {
        String idNormalizado = normalizarIdProduto(idOuNumero);
        String caminhoJson = montarCaminhoJson(idNormalizado);

        Optional<String> jsonOpt = gcsService.lerObjetoComoTexto(caminhoJson);
        if (jsonOpt.isEmpty()) {
            return Optional.empty();
        }

        try {
            Produto produto = objectMapper.readValue(jsonOpt.get(), Produto.class);
            return Optional.of(produto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erro ao converter o JSON do objeto '" + caminhoJson + "': " + e.getMessage(), e);
        }
    }

    /**
     * Exclui o arquivo JSON do produto no bucket e, caso possua imagem vinculada,
     * também remove o arquivo de imagem correspondente.
     */
    public boolean excluirProduto(String idOuNumero) {
        String idNormalizado = normalizarIdProduto(idOuNumero);
        Optional<Produto> produtoOpt = buscarProdutoPorId(idNormalizado);

        if (produtoOpt.isEmpty()) {
            return false;
        }

        Produto produto = produtoOpt.get();
        if (produto.getCaminhoObjetoImagem() != null && !produto.getCaminhoObjetoImagem().isBlank()) {
            gcsService.excluirObjeto(produto.getCaminhoObjetoImagem());
        }

        return gcsService.excluirObjeto(montarCaminhoJson(idNormalizado));
    }

    /**
     * Converte o objeto Produto em JSON formatado e envia para o bucket do GCS.
     */
    private void salvarProdutoNoBucket(Produto produto) {
        try {
            String conteudoJson = objectMapper.writeValueAsString(produto);
            gcsService.uploadTextoUtf8(
                    produto.getCaminhoObjetoJson(),
                    conteudoJson,
                    "application/json; charset=utf-8"
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erro ao serializar o produto para JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Permite que o usuário digite "1", "001" ou "produto-001" e normaliza para "produto-001".
     */
    public String normalizarIdProduto(String entrada) {
        if (entrada == null || entrada.isBlank()) {
            throw new IllegalArgumentException("O identificador do produto não pode ser vazio.");
        }
        String limpo = entrada.trim().toLowerCase(Locale.ROOT);
        if (limpo.endsWith(".json")) {
            limpo = limpo.substring(0, limpo.length() - 5);
        }
        if (limpo.startsWith("produtos/")) {
            limpo = limpo.substring("produtos/".length());
        }
        if (limpo.matches("\\d+")) {
            return String.format("produto-%03d", Integer.parseInt(limpo));
        }
        if (!limpo.startsWith("produto-")) {
            return "produto-" + limpo;
        }
        return limpo;
    }

    public String montarCaminhoJson(String idNormalizado) {
        return PREFIXO_PRODUTOS + idNormalizado + ".json";
    }

    private String extrairExtensaoValida(String nomeArquivo) {
        int ultimoPonto = nomeArquivo.lastIndexOf('.');
        if (ultimoPonto == -1 || ultimoPonto == nomeArquivo.length() - 1) {
            throw new IllegalArgumentException("O arquivo de imagem precisa ter uma extensão válida (.jpg, .jpeg, .png, .webp).");
        }
        String ext = nomeArquivo.substring(ultimoPonto + 1);
        return switch (ext) {
            case "jpg", "jpeg", "png", "webp", "gif" -> ext;
            default -> throw new IllegalArgumentException(
                    "Extensão de imagem não suportada ('." + ext + "'). Utilize .jpg, .jpeg, .png, .webp ou .gif."
            );
        };
    }

    private String determinarContentTypeImagem(String extensao) {
        return switch (extensao) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }
}
