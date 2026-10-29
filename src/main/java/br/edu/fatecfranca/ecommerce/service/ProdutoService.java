package br.edu.fatecfranca.ecommerce.service;

import br.edu.fatecfranca.ecommerce.database.DatabaseService;
import br.edu.fatecfranca.ecommerce.model.Produto;
import br.edu.fatecfranca.ecommerce.model.ProdutoImagem;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Serviço de domínio responsável pelas regras de negócio dos produtos do e-commerce.
 *
 * Em conformidade com o projeto:
 * - Os dados dos registros (entidade, referências às imagens, flag de capa, datas)
 *   residem no Banco de Dados Local SQLite (ecommerce.db).
 * - Apenas os arquivos binários de imagem são enviados para o bucket Google Cloud Storage
 *   (prefixo 'imagens/').
 */
public class ProdutoService {

    public static final String PREFIXO_IMAGENS = "imagens/";

    private final GcsStorageService gcsService;
    private final DatabaseService databaseService;

    public ProdutoService(GcsStorageService gcsService, DatabaseService databaseService) {
        this.gcsService = gcsService;
        this.databaseService = databaseService;
    }

    public DatabaseService getDatabaseService() {
        return databaseService;
    }

    public GcsStorageService getGcsService() {
        return gcsService;
    }

    /**
     * Gera o próximo identificador sequencial único no banco local (ex: produto-001, produto-002).
     */
    public String gerarProximoId() {
        int proximoNumero = databaseService.gerarProximoNumeroId();
        String idCandidato = String.format("produto-%03d", proximoNumero);
        while (databaseService.buscarProdutoPorId(idCandidato).isPresent()) {
            proximoNumero++;
            idCandidato = String.format("produto-%03d", proximoNumero);
        }
        return idCandidato;
    }

    /**
     * Cadastra um novo produto no banco de dados local SQLite.
     */
    public Produto cadastrarProduto(String nome, String descricao, BigDecimal preco, String categoria) {
        String id = gerarProximoId();
        Produto produto = new Produto(id, nome, descricao, preco, categoria);
        produto.setDataCadastro(LocalDateTime.now());
        databaseService.salvarProduto(produto);
        return produto;
    }

    /**
     * Realiza o upload de uma imagem local para "imagens/{produtoId}/{nomeArquivo}" no bucket do GCS
     * e grava os metadados e o flag de imagem principal na tabela 'produto_imagens' do SQLite.
     */
    public ProdutoImagem anexarImagemAoProduto(String idOuNumero, Path caminhoImagemLocal, boolean marcarComoPrincipal) {
        String idNormalizado = normalizarIdProduto(idOuNumero);
        Produto produto = buscarProdutoPorId(idNormalizado)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Produto '" + idNormalizado + "' não encontrado no banco de dados local. Cadastre-o antes de enviar a imagem."
                ));

        String nomeOriginal = caminhoImagemLocal.getFileName().toString();
        String extensao = extrairExtensaoValida(nomeOriginal);
        String contentType = determinarContentTypeImagem(extensao);

        String nomeSanitizado = sanitizarNomeArquivo(nomeOriginal);
        String nomeFinalObjeto = System.currentTimeMillis() + "-" + nomeSanitizado;
        String caminhoObjetoNoBucket = PREFIXO_IMAGENS + idNormalizado + "/" + nomeFinalObjeto;

        gcsService.uploadArquivo(caminhoObjetoNoBucket, caminhoImagemLocal, contentType);

        String urlPublica = gcsService.gerarUrlPublica(caminhoObjetoNoBucket);
        ProdutoImagem novaImagem = new ProdutoImagem(
                idNormalizado,
                caminhoObjetoNoBucket,
                urlPublica,
                nomeOriginal,
                marcarComoPrincipal
        );

        return databaseService.inserirImagem(novaImagem);
    }

    /**
     * Realiza o upload de uma imagem em bytes (ex: enviada via HTTP ou gerada em memória) para o bucket do GCS
     * e grava a referência na tabela 'produto_imagens' do SQLite.
     */
    public ProdutoImagem anexarImagemBytesAoProduto(String idOuNumero, byte[] bytesImagem, String nomeOriginal, String contentType, boolean marcarComoPrincipal) {
        String idNormalizado = normalizarIdProduto(idOuNumero);
        Produto produto = buscarProdutoPorId(idNormalizado)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Produto '" + idNormalizado + "' não encontrado no banco de dados local. Cadastre-o antes de enviar a imagem."
                ));

        String extensao = extrairExtensaoValida(nomeOriginal);
        if (contentType == null || contentType.isBlank() || contentType.equals("application/octet-stream")) {
            contentType = determinarContentTypeImagem(extensao);
        }

        String nomeSanitizado = sanitizarNomeArquivo(nomeOriginal);
        String nomeFinalObjeto = System.currentTimeMillis() + "-" + nomeSanitizado;
        String caminhoObjetoNoBucket = PREFIXO_IMAGENS + idNormalizado + "/" + nomeFinalObjeto;

        gcsService.uploadBytes(caminhoObjetoNoBucket, bytesImagem, contentType);

        String urlPublica = gcsService.gerarUrlPublica(caminhoObjetoNoBucket);
        ProdutoImagem novaImagem = new ProdutoImagem(
                idNormalizado,
                caminhoObjetoNoBucket,
                urlPublica,
                nomeOriginal,
                marcarComoPrincipal
        );

        return databaseService.inserirImagem(novaImagem);
    }

    /**
     * Define qual imagem do produto atuará como a imagem principal (capa do catálogo).
     */
    public boolean definirImagemPrincipal(String idOuNumero, Long imagemId) {
        String idNormalizado = normalizarIdProduto(idOuNumero);
        return databaseService.definirImagemPrincipal(idNormalizado, imagemId);
    }

    /**
     * Exclui uma imagem individual: remove o arquivo binário do bucket GCS e a referência no SQLite.
     * Caso a imagem apagada fosse a capa, outra imagem é promovida automaticamente a principal.
     */
    public boolean excluirImagemIndividual(String idOuNumero, Long imagemId) {
        String idNormalizado = normalizarIdProduto(idOuNumero);
        Optional<ProdutoImagem> imgOpt = databaseService.buscarImagemPorId(imagemId);
        if (imgOpt.isEmpty()) {
            return false;
        }

        ProdutoImagem img = imgOpt.get();
        if (!idNormalizado.equalsIgnoreCase(img.getProdutoId())) {
            throw new IllegalArgumentException("A imagem informada não pertence ao produto " + idNormalizado);
        }

        // Remove o arquivo físico do bucket no Google Cloud Storage
        try {
            gcsService.excluirObjeto(img.getCaminhoObjeto());
        } catch (Exception e) {
            System.err.println("[AVISO] Falha ao excluir objeto no GCS (" + img.getCaminhoObjeto() + "): " + e.getMessage());
        }

        // Remove do SQLite
        return databaseService.excluirImagem(imagemId);
    }

    /**
     * Lista todos os produtos cadastrados no SQLite com suas imagens associadas.
     */
    public List<Produto> listarProdutos() {
        return databaseService.listarProdutos();
    }

    /**
     * Consulta um produto específico pelo seu ID (ex: "produto-001" ou "1") trazendo todas as suas fotos.
     */
    public Optional<Produto> buscarProdutoPorId(String idOuNumero) {
        String idNormalizado = normalizarIdProduto(idOuNumero);
        return databaseService.buscarProdutoPorId(idNormalizado);
    }

    /**
     * Exclui um produto do banco de dados local SQLite e remove todas as suas imagens do GCS.
     */
    public boolean excluirProduto(String idOuNumero) {
        String idNormalizado = normalizarIdProduto(idOuNumero);
        Optional<Produto> produtoOpt = buscarProdutoPorId(idNormalizado);
        if (produtoOpt.isEmpty()) {
            return false;
        }

        Produto produto = produtoOpt.get();
        // Remove todos os arquivos de imagem associados no bucket do Google Cloud Storage
        for (ProdutoImagem img : produto.getImagens()) {
            try {
                gcsService.excluirObjeto(img.getCaminhoObjeto());
            } catch (Exception e) {
                System.err.println("[AVISO] Falha ao excluir objeto '" + img.getCaminhoObjeto() + "' do GCS: " + e.getMessage());
            }
        }

        return databaseService.excluirProduto(idNormalizado);
    }

    /**
     * Executa a carga de demonstração com 10 produtos e 3 imagens reais para cada produto
     * (total de 30 imagens enviadas ao bucket fatec-2026-grupo-i, com as capas definidas),
     * atendendo com nota máxima o Requisito Técnico 5 do trabalho.
     */
    public int popularCargaDemonstracao() {
        record DemoItem(String nome, String categoria, BigDecimal preco, String descricao,
                        Color corPrimaria, Color corSecundaria, String detalhe1, String detalhe2) {}

        List<DemoItem> itens = List.of(
                new DemoItem(
                        "Smartphone Galaxy Pro Max 5G",
                        "Smartphones",
                        new BigDecimal("4899.00"),
                        "Tela Dynamic AMOLED 6.8\" 120Hz, Processador Octa-Core, 256GB, Câmera Quádrupla de 200MP e Bateria de 5000mAh.",
                        new Color(30, 41, 59), new Color(59, 130, 246),
                        "Sensor 200MP · Zoom Óptico 10x", "Cabo USB-C, Carregador 45W e Manual"
                ),
                new DemoItem(
                        "Notebook Gamer Nitro X16",
                        "Informática",
                        new BigDecimal("6299.90"),
                        "Intel Core i7 13ª Geração, 16GB RAM DDR5, SSD 1TB NVMe, Placa de Vídeo RTX 4060 8GB e Tela 16\" IPS 165Hz.",
                        new Color(15, 23, 42), new Color(220, 38, 38),
                        "RTX 4060 8GB · Cooler Dual Fan", "Fonte 230W Bivolt e Mochila Gamer"
                ),
                new DemoItem(
                        "Monitor UltraWide Curvo 34\"",
                        "Monitores",
                        new BigDecimal("2450.00"),
                        "Resolução WQHD 3440x1440, Curvatura 1500R, HDR400, Taxa de 144Hz 1ms e Entradas DisplayPort e HDMI 2.1.",
                        new Color(24, 24, 27), new Color(13, 148, 136),
                        "Painel Curvo 1500R · 144Hz 1ms", "Suporte Articulado, Cabo DP e Cabo HDMI"
                ),
                new DemoItem(
                        "Cadeira Ergonômica Pro Mesh",
                        "Escritório",
                        new BigDecimal("1189.90"),
                        "Estrutura reforçada em nylon, encosto em malha respirável Mesh, apoio lombar ajustável 3D e rodízios silenciosos.",
                        new Color(38, 38, 38), new Color(16, 185, 129),
                        "Apoio Lombar 3D · Pistão Classe 4", "Kit Parafusos, Chave Allen e Manual"
                ),
                new DemoItem(
                        "Teclado Mecânico RGB Hot-Swap",
                        "Periféricos",
                        new BigDecimal("489.00"),
                        "Switches mecânicos pré-lubrificados, Keycaps Double-Shot PBT, Iluminação RGB per-key e Conexão Tri-Mode (BT/2.4G/Cabo).",
                        new Color(31, 41, 55), new Color(168, 85, 247),
                        "Switches Hot-Swap · Tri-Mode", "Extrator de Switches, Keycaps Extras e Cabo"
                ),
                new DemoItem(
                        "Mouse Sem Fio Ultraleve 26K DPI",
                        "Periféricos",
                        new BigDecimal("329.90"),
                        "Sensor óptico PixArt 26.000 DPI, peso ultraleve de apenas 54 gramas, switches ópticos e bateria de até 80 horas.",
                        new Color(17, 24, 39), new Color(245, 158, 11),
                        "Peso 54g · Sensor PixArt 26K", "Dongle 2.4G, Cabo Paracord e Grip Tape"
                ),
                new DemoItem(
                        "Headset Bluetooth ANC 7.1",
                        "Áudio",
                        new BigDecimal("799.00"),
                        "Cancelamento Ativo de Ruído (ANC Híbrido), drivers de 50mm de titânio, microfone retrátil com IA e áudio espacial 7.1.",
                        new Color(23, 23, 23), new Color(6, 182, 212),
                        "Cancelamento Ativo Ruído · Bateria 60h", "Case Rígido, Cabo P3 e Cabo USB-C"
                ),
                new DemoItem(
                        "Smartwatch Sport AMOLED GPS",
                        "Wearables",
                        new BigDecimal("899.00"),
                        "Tela AMOLED de 1.43\" Always-On, GPS integrado de dupla frequência, monitoramento cardíaco/SpO2 e resistência à água 5ATM.",
                        new Color(24, 24, 27), new Color(236, 72, 153),
                        "GPS Integrado · 5ATM À Prova D'água", "Pulseira Silicone, Cabo Magnético e Guia"
                ),
                new DemoItem(
                        "Câmera Mirrorless 4K 60FPS",
                        "Fotografia",
                        new BigDecimal("5490.00"),
                        "Sensor CMOS APS-C de 24.2 MP, gravação de vídeo 4K HDR a 60fps sem corte, foco automático híbrido e estabilização de 5 eixos.",
                        new Color(15, 23, 42), new Color(234, 88, 12),
                        "Sensor 24.2MP · Estabilização 5 Eixos", "Lente 18-55mm, Bateria, Carregador e Alça"
                ),
                new DemoItem(
                        "Caixa de Som Portátil 60W IPX7",
                        "Áudio",
                        new BigDecimal("549.90"),
                        "Potência estéreo de 60W RMS com radiadores passivos de graves, proteção IPX7 à prova d'água e bateria para até 24 horas de reprodução.",
                        new Color(30, 41, 59), new Color(34, 197, 94),
                        "Potência 60W RMS · IPX7 À Prova D'água", "Cabo USB-C, Alça de Transporte e Manual"
                )
        );

        int imagensEnviadas = 0;

        for (DemoItem item : itens) {
            Produto produto = cadastrarProduto(item.nome(), item.descricao(), item.preco(), item.categoria());

            // 1ª Imagem: Capa / Vista Principal (marcada como ehPrincipal = true)
            byte[] imgCapa = gerarImagemDemo(produto, "1. VISTA PRINCIPAL (CAPA)", item.corPrimaria(), item.corSecundaria(), "Foto de Catálogo Oficial", true);
            anexarImagemBytesAoProduto(produto.getId(), imgCapa, "01-capa-frontal.png", "image/png", true);
            imagensEnviadas++;

            // 2ª Imagem: Detalhes Técnicos e Ângulo Lateral
            byte[] imgDetalhes = gerarImagemDemo(produto, "2. DETALHES & RECURSOS", item.corPrimaria().darker(), item.corSecundaria(), item.detalhe1(), false);
            anexarImagemBytesAoProduto(produto.getId(), imgDetalhes, "02-detalhes-recursos.png", "image/png", false);
            imagensEnviadas++;

            // 3ª Imagem: Conteúdo da Embalagem / Acessórios
            byte[] imgEmbalagem = gerarImagemDemo(produto, "3. EMBALAGEM & ITENS", new Color(20, 24, 30), item.corSecundaria(), item.detalhe2(), false);
            anexarImagemBytesAoProduto(produto.getId(), imgEmbalagem, "03-conteudo-embalagem.png", "image/png", false);
            imagensEnviadas++;
        }

        return imagensEnviadas;
    }

    /**
     * Gera dinamicamente um arquivo PNG de alta qualidade para demonstração visual
     * sem precisar de arquivos externos.
     */
    private byte[] gerarImagemDemo(Produto produto, String subtitulo, Color bg1, Color bg2, String destaqueExtra, boolean ehCapa) {
        int largura = 700;
        int altura = 460;
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();

        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Gradiente de fundo elegante
            g.setPaint(new GradientPaint(0, 0, bg1, largura, altura, bg2));
            g.fillRect(0, 0, largura, altura);

            // Moldura interna translúcida
            g.setColor(new Color(255, 255, 255, 25));
            g.fillRoundRect(25, 25, largura - 50, altura - 50, 24, 24);
            g.setColor(new Color(255, 255, 255, 60));
            g.drawRoundRect(25, 25, largura - 50, altura - 50, 24, 24);

            // Badge superior: Categoria e ID
            g.setColor(new Color(255, 255, 255, 220));
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.drawString("FATECSHOP CLOUD  •  " + produto.getCategoria().toUpperCase(Locale.ROOT), 50, 65);

            // Badge de Capa ou Galeria
            if (ehCapa) {
                g.setColor(new Color(234, 179, 8)); // Dourado
                g.fillRoundRect(largura - 240, 48, 190, 28, 14, 14);
                g.setColor(new Color(15, 23, 42));
                g.setFont(new Font("SansSerif", Font.BOLD, 12));
                g.drawString("★ IMAGEM PRINCIPAL (CAPA)", largura - 232, 67);
            } else {
                g.setColor(new Color(255, 255, 255, 40));
                g.fillRoundRect(largura - 210, 48, 160, 28, 14, 14);
                g.setColor(Color.WHITE);
                g.setFont(new Font("SansSerif", Font.BOLD, 12));
                g.drawString("GALERIA DE FOTOS", largura - 195, 67);
            }

            // Título do Produto
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 26));
            String nomeExibicao = produto.getNome();
            if (nomeExibicao.length() > 32) {
                nomeExibicao = nomeExibicao.substring(0, 29) + "...";
            }
            g.drawString(nomeExibicao, 50, 125);

            // Subtítulo do Ângulo/Foto
            g.setColor(new Color(226, 232, 240));
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            g.drawString(subtitulo, 50, 165);

            // Destaque extra / Especificações
            g.setColor(new Color(191, 219, 254));
            g.setFont(new Font("SansSerif", Font.PLAIN, 16));
            g.drawString("» " + destaqueExtra, 50, 210);

            // Preço destacado
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 34));
            String precoStr = "R$ " + (produto.getPreco() != null ? produto.getPreco().toPlainString() : "0.00");
            g.drawString(precoStr, 50, 290);

            // Rodapé técnico com identificação acadêmica do trabalho
            g.setColor(new Color(255, 255, 255, 30));
            g.drawLine(50, 360, largura - 50, 360);

            g.setColor(new Color(203, 213, 225));
            g.setFont(new Font("Monospaced", Font.PLAIN, 13));
            g.drawString("Bucket GCS: fatec-2026-grupo-i  |  Registro: " + produto.getId(), 50, 395);
            g.drawString("Fatec Franca DSM  |  Hugo Rodrigues, Pablo Nóbrega, Arthur Ferreira", 50, 420);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(imagem, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar imagem gráfica de demonstração: " + e.getMessage(), e);
        } finally {
            g.dispose();
        }
    }

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

    private String sanitizarNomeArquivo(String nomeOriginal) {
        if (nomeOriginal == null || nomeOriginal.isBlank()) {
            return "imagem.png";
        }
        return nomeOriginal.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String extrairExtensaoValida(String nomeArquivo) {
        int ultimoPonto = nomeArquivo.lastIndexOf('.');
        if (ultimoPonto == -1 || ultimoPonto == nomeArquivo.length() - 1) {
            throw new IllegalArgumentException("O arquivo de imagem precisa ter uma extensão válida (.jpg, .jpeg, .png, .webp).");
        }
        String ext = nomeArquivo.substring(ultimoPonto + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "jpg", "jpeg", "png", "webp", "gif" -> ext;
            default -> throw new IllegalArgumentException(
                    "Extensão de imagem não suportada ('." + ext + "'). Utilize .jpg, .jpeg, .png, .webp ou .gif."
            );
        };
    }

    private String determinarContentTypeImagem(String extensao) {
        return switch (extensao.toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }
}
