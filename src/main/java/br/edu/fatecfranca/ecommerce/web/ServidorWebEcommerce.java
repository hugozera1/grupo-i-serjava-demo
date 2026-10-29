package br.edu.fatecfranca.ecommerce.web;

import br.edu.fatecfranca.ecommerce.model.Produto;
import br.edu.fatecfranca.ecommerce.model.ProdutoImagem;
import br.edu.fatecfranca.ecommerce.service.GcsStorageService;
import br.edu.fatecfranca.ecommerce.service.ProdutoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP embutido em Java que expõe a API REST para o Frontend Web do E-commerce
 * e serve os arquivos estáticos (HTML, CSS e JavaScript) em http://localhost:8080.
 *
 * Integra o Banco de Dados Local SQLite (para metadados, entidades e capa)
 * com o Google Cloud Storage (exclusivo para arquivos binários de imagem).
 */
public class ServidorWebEcommerce {

    private final int porta;
    private final GcsStorageService gcsService;
    private final ProdutoService produtoService;
    private final ObjectMapper objectMapper;
    private HttpServer server;

    public ServidorWebEcommerce(int porta, GcsStorageService gcsService, ProdutoService produtoService) {
        this.porta = porta;
        this.gcsService = gcsService;
        this.produtoService = produtoService;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void iniciar() throws IOException {
        server = HttpServer.create(new InetSocketAddress(porta), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        // Rotas da API REST integrada ao Google Cloud Storage e SQLite local
        server.createContext("/api/info", this::tratarInfoBucket);
        server.createContext("/api/produtos", this::tratarProdutos);
        server.createContext("/api/objetos", this::tratarObjetosBucket);
        server.createContext("/api/demo", this::tratarDemo);

        // Rota para servir o Frontend Web (HTML, CSS, JS)
        server.createContext("/", this::tratarArquivosEstaticos);

        server.start();
    }

    public void parar() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * GET /api/info -> Retorna metadados do bucket, do SQLite e do grupo para o painel Web.
     */
    private void tratarInfoBucket(HttpExchange exchange) throws IOException {
        adicionarCabecalhosCors(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("projeto", GcsStorageService.PROJECT_ID);
        info.put("bucket", GcsStorageService.BUCKET_NAME);
        info.put("urlPublicaBase", GcsStorageService.PUBLIC_BASE_URL);
        info.put("bancoDados", "SQLite local (ecommerce.db)");
        info.put("totalProdutos", produtoService.getDatabaseService().contarProdutos());
        info.put("totalImagens", produtoService.getDatabaseService().contarImagens());
        info.put("localizacao", "us-central1");
        info.put("classeArmazenamento", "STANDARD");
        info.put("serviceAccount", "sa-grupo-i@serjava-demo.iam.gserviceaccount.com");
        info.put("grupo", "grupo-i");
        info.put("integrantes", List.of(
                "Hugo de Castro Rodrigues",
                "Pablo Miguel Sousa Nóbrega",
                "Arthur Rodrigues Ferreira"
        ));

        enviarJson(exchange, 200, info);
    }

    /**
     * POST /api/demo/seed -> Executa a carga de demonstração com 10 produtos e 3 imagens cada.
     */
    private void tratarDemo(HttpExchange exchange) throws IOException {
        adicionarCabecalhosCors(exchange);
        String metodo = exchange.getRequestMethod().toUpperCase();
        if ("OPTIONS".equals(metodo)) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if (("POST".equals(metodo) || "GET".equals(metodo)) && (path.endsWith("/seed") || path.endsWith("/demo"))) {
            try {
                int fotosEnviadas = produtoService.popularCargaDemonstracao();
                enviarJson(exchange, 200, Map.of(
                        "sucesso", true,
                        "mensagem", "Carga de demonstração executada com sucesso! 10 produtos cadastrados e " + fotosEnviadas + " imagens enviadas para o bucket GCS.",
                        "produtosCadastrados", 10,
                        "imagensEnviadas", fotosEnviadas
                ));
            } catch (Exception e) {
                enviarErro(exchange, 500, "Erro ao popular demonstração: " + e.getMessage());
            }
            return;
        }

        enviarErro(exchange, 404, "Rota não encontrada.");
    }

    /**
     * Gerencia as rotas de produtos:
     * - GET    /api/produtos                                    -> Lista produtos (com capa e total de fotos)
     * - POST   /api/produtos                                    -> Cadastra produto no SQLite
     * - GET    /api/produtos/{id}                               -> Consulta produto com todas as fotos
     * - DELETE /api/produtos/{id}                               -> Exclui produto do SQLite e imagens do GCS
     * - POST   /api/produtos/{id}/imagens                       -> Envia nova foto para o produto no GCS
     * - PUT    /api/produtos/{id}/imagens/{imgId}/principal     -> Marca foto como capa
     * - DELETE /api/produtos/{id}/imagens/{imgId}               -> Exclui foto individualmente
     */
    private void tratarProdutos(HttpExchange exchange) throws IOException {
        adicionarCabecalhosCors(exchange);
        String metodo = exchange.getRequestMethod().toUpperCase();
        if ("OPTIONS".equals(metodo)) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String sufixo = path.substring("/api/produtos".length());
        if (sufixo.startsWith("/")) {
            sufixo = sufixo.substring(1);
        }

        try {
            // Rota raiz: /api/produtos
            if (sufixo.isEmpty()) {
                if ("GET".equals(metodo)) {
                    List<Produto> produtos = produtoService.listarProdutos();
                    enviarJson(exchange, 200, produtos);
                    return;
                }
                if ("POST".equals(metodo)) {
                    criarProdutoViaJson(exchange);
                    return;
                }
                enviarErro(exchange, 405, "Método não permitido.");
                return;
            }

            // Sub-rotas: /api/produtos/...
            String[] partes = sufixo.split("/");
            String idProduto = URLDecoder.decode(partes[0], StandardCharsets.UTF_8);

            // POST /api/produtos/{id}/imagem (compatibilidade anterior)
            if (partes.length == 2 && ("imagem".equalsIgnoreCase(partes[1]) || "imagens".equalsIgnoreCase(partes[1]))) {
                if ("POST".equals(metodo)) {
                    uploadImagemViaStream(exchange, idProduto);
                    return;
                }
                enviarErro(exchange, 405, "Use POST para enviar imagem.");
                return;
            }

            // Ações específicas sobre imagens:
            // PUT  /api/produtos/{id}/imagens/{imagemId}/principal
            // POST /api/produtos/{id}/imagens/{imagemId}/principal
            if (partes.length == 4 && "imagens".equalsIgnoreCase(partes[1]) && "principal".equalsIgnoreCase(partes[3])) {
                if ("PUT".equals(metodo) || "POST".equals(metodo)) {
                    Long imagemId = Long.parseLong(partes[2]);
                    boolean sucesso = produtoService.definirImagemPrincipal(idProduto, imagemId);
                    if (!sucesso) {
                        enviarErro(exchange, 404, "Imagem não encontrada.");
                    } else {
                        enviarJson(exchange, 200, Map.of(
                                "sucesso", true,
                                "mensagem", "Imagem definida como principal (capa) com sucesso."
                        ));
                    }
                    return;
                }
                enviarErro(exchange, 405, "Use PUT ou POST para definir imagem principal.");
                return;
            }

            // DELETE /api/produtos/{id}/imagens/{imagemId}
            if (partes.length == 3 && "imagens".equalsIgnoreCase(partes[1])) {
                if ("DELETE".equals(metodo)) {
                    Long imagemId = Long.parseLong(partes[2]);
                    boolean removido = produtoService.excluirImagemIndividual(idProduto, imagemId);
                    if (!removido) {
                        enviarErro(exchange, 404, "Imagem não encontrada para exclusão.");
                    } else {
                        enviarJson(exchange, 200, Map.of(
                                "sucesso", true,
                                "mensagem", "Imagem removida do Google Cloud Storage e do banco local com sucesso."
                        ));
                    }
                    return;
                }
                enviarErro(exchange, 405, "Use DELETE para apagar imagem.");
                return;
            }

            // Operações no próprio produto:
            // GET /api/produtos/{id}
            // DELETE /api/produtos/{id}
            if (partes.length == 1) {
                if ("GET".equals(metodo)) {
                    Optional<Produto> prodOpt = produtoService.buscarProdutoPorId(idProduto);
                    if (prodOpt.isEmpty()) {
                        enviarErro(exchange, 404, "Produto '" + idProduto + "' não encontrado.");
                    } else {
                        enviarJson(exchange, 200, prodOpt.get());
                    }
                    return;
                }
                if ("DELETE".equals(metodo)) {
                    boolean removido = produtoService.excluirProduto(idProduto);
                    if (!removido) {
                        enviarErro(exchange, 404, "Produto '" + idProduto + "' não encontrado para exclusão.");
                    } else {
                        enviarJson(exchange, 200, Map.of(
                                "sucesso", true,
                                "mensagem", "Produto '" + produtoService.normalizarIdProduto(idProduto) + "' e suas imagens foram excluídos com sucesso."
                        ));
                    }
                    return;
                }
            }

            enviarErro(exchange, 404, "Rota de produto não encontrada.");
        } catch (IllegalArgumentException e) {
            enviarErro(exchange, 400, e.getMessage());
        } catch (Exception e) {
            enviarErro(exchange, 500, e.getMessage());
        }
    }

    private void criarProdutoViaJson(HttpExchange exchange) throws IOException {
        String corpo = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode node = objectMapper.readTree(corpo);

        String nome = extrairTexto(node, "nome");
        String categoria = extrairTexto(node, "categoria");
        String descricao = extrairTexto(node, "descricao");
        String precoStr = extrairTexto(node, "preco").replace(",", ".");

        if (nome.length() < 3) {
            throw new IllegalArgumentException("O nome do produto deve ter pelo menos 3 caracteres.");
        }
        if (categoria.length() < 2) {
            throw new IllegalArgumentException("Informe uma categoria válida.");
        }
        if (descricao.length() < 5) {
            throw new IllegalArgumentException("A descrição deve ter pelo menos 5 caracteres.");
        }

        BigDecimal preco;
        try {
            preco = new BigDecimal(precoStr).setScale(2, RoundingMode.HALF_UP);
            if (preco.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("O preço deve ser maior que zero.");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Preço inválido. Informe um valor numérico positivo.");
        }

        Produto criado = produtoService.cadastrarProduto(nome, descricao, preco, categoria);
        enviarJson(exchange, 201, criado);
    }

    private void uploadImagemViaStream(HttpExchange exchange, String idProduto) throws IOException {
        Map<String, String> params = lerQueryParams(exchange.getRequestURI());
        String nomeArquivo = params.getOrDefault("nomeArquivo", "imagem.png");
        boolean ehPrincipal = Boolean.parseBoolean(params.getOrDefault("principal", "false"));

        int idxPonto = nomeArquivo.lastIndexOf('.');
        String sufixoExt = (idxPonto != -1) ? nomeArquivo.substring(idxPonto) : ".png";

        Path arquivoTemp = Files.createTempFile("upload-gcs-", sufixoExt);
        try {
            try (InputStream in = exchange.getRequestBody()) {
                Files.copy(in, arquivoTemp, StandardCopyOption.REPLACE_EXISTING);
            }
            ProdutoImagem imagemCriada = produtoService.anexarImagemAoProduto(idProduto, arquivoTemp, ehPrincipal);
            enviarJson(exchange, 200, imagemCriada);
        } finally {
            Files.deleteIfExists(arquivoTemp);
        }
    }

    /**
     * Rotas para explorar imagens diretamente no bucket:
     * - GET /api/objetos                           -> Lista objetos em imagens/
     * - GET /api/objetos/download?caminho=...      -> Download direto do objeto
     */
    private void tratarObjetosBucket(HttpExchange exchange) throws IOException {
        adicionarCabecalhosCors(exchange);
        String metodo = exchange.getRequestMethod().toUpperCase();
        if ("OPTIONS".equals(metodo)) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        Map<String, String> query = lerQueryParams(exchange.getRequestURI());

        try {
            if (path.equals("/api/objetos") || path.equals("/api/objetos/")) {
                List<String> imagens = gcsService.listarObjetosPorPrefixo(ProdutoService.PREFIXO_IMAGENS);

                Map<String, Object> resposta = new LinkedHashMap<>();
                resposta.put("bucket", GcsStorageService.BUCKET_NAME);
                resposta.put("urlPublicaBase", GcsStorageService.PUBLIC_BASE_URL);
                resposta.put("objetosImagens", imagens);
                enviarJson(exchange, 200, resposta);
                return;
            }

            if (path.equals("/api/objetos/download")) {
                String caminho = query.getOrDefault("caminho", "");
                if (caminho.isBlank()) {
                    enviarErro(exchange, 400, "Informe o parâmetro 'caminho'.");
                    return;
                }
                String nomeArquivo = Path.of(caminho).getFileName().toString();
                Path destinoLocal = Path.of("downloads", nomeArquivo);

                boolean baixado = gcsService.baixarObjetoParaArquivo(caminho, destinoLocal);
                if (!baixado) {
                    enviarErro(exchange, 404, "Objeto '" + caminho + "' não encontrado no bucket.");
                    return;
                }

                byte[] bytes = Files.readAllBytes(destinoLocal);
                exchange.getResponseHeaders().set("Content-Type", determinarContentType(nomeArquivo));
                exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + nomeArquivo + "\"");
                exchange.getResponseHeaders().set("X-Saved-Local-Path", destinoLocal.toAbsolutePath().toString());
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }

            enviarErro(exchange, 404, "Rota não encontrada.");
        } catch (Exception e) {
            enviarErro(exchange, 500, e.getMessage());
        }
    }

    private void tratarArquivosEstaticos(HttpExchange exchange) throws IOException {
        String caminho = exchange.getRequestURI().getPath();
        if (caminho.equals("/") || caminho.isBlank()) {
            caminho = "/index.html";
        }

        if (caminho.contains("..")) {
            enviarErro(exchange, 403, "Acesso negado.");
            return;
        }

        byte[] bytes = carregarRecursoWeb(caminho);
        if (bytes == null) {
            enviarErro(exchange, 404, "Arquivo não encontrado: " + caminho);
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", determinarContentType(caminho));
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private byte[] carregarRecursoWeb(String caminhoRelativo) throws IOException {
        Path arquivoDisco = Path.of("src", "main", "resources", "web" + caminhoRelativo);
        if (Files.exists(arquivoDisco) && Files.isRegularFile(arquivoDisco)) {
            return Files.readAllBytes(arquivoDisco);
        }
        try (InputStream is = getClass().getResourceAsStream("/web" + caminhoRelativo)) {
            if (is != null) {
                return is.readAllBytes();
            }
        }
        return null;
    }

    private void enviarJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        byte[] bytes = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void enviarErro(HttpExchange exchange, int statusCode, String mensagem) throws IOException {
        Map<String, Object> erro = new LinkedHashMap<>();
        erro.put("erro", true);
        erro.put("status", statusCode);
        erro.put("mensagem", mensagem);
        enviarJson(exchange, statusCode, erro);
    }

    private void adicionarCabecalhosCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-File-Name");
    }

    private String extrairTexto(JsonNode node, String campo) {
        if (node == null || !node.hasNonNull(campo)) {
            return "";
        }
        return node.get(campo).asText("").trim();
    }

    private Map<String, String> lerQueryParams(URI uri) {
        Map<String, String> mapa = new HashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return mapa;
        }
        for (String par : query.split("&")) {
            int idx = par.indexOf('=');
            if (idx > 0) {
                String chave = URLDecoder.decode(par.substring(0, idx), StandardCharsets.UTF_8);
                String valor = URLDecoder.decode(par.substring(idx + 1), StandardCharsets.UTF_8);
                mapa.put(chave, valor);
            }
        }
        return mapa;
    }

    private String determinarContentType(String caminho) {
        String lower = caminho.toLowerCase();
        if (lower.endsWith(".html")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }
}
