package br.edu.fatecfranca.ecommerce;

import br.edu.fatecfranca.ecommerce.model.Produto;
import br.edu.fatecfranca.ecommerce.service.GcsStorageService;
import br.edu.fatecfranca.ecommerce.service.ProdutoService;
import br.edu.fatecfranca.ecommerce.util.ValidadorEntrada;
import br.edu.fatecfranca.ecommerce.web.ServidorWebEcommerce;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import javax.imageio.ImageIO;

/**
 * Ponto de entrada da aplicação de gerenciamento de produtos de E-commerce
 * com armazenamento no Google Cloud Storage (GCS).
 *
 * Inicia simultaneamente:
 * 1. A Interface Gráfica Web do E-commerce (HTML/CSS/JS) em http://localhost:8080
 * 2. O Menu Interativo no Console
 *
 * Fatec Franca - Desenvolvimento de Software Multiplataforma
 * Grupo I: Hugo de Castro Rodrigues, Pablo Miguel Sousa Nóbrega, Arthur Rodrigues Ferreira
 */
public class Main {

    public static final int PORTA_WEB = 8080;

    public static void main(String[] args) {
        // Garante saída e leitura em UTF-8 no console do Windows
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        garantirImagemAmostraLocal();
        exibirCabecalho();

        GcsStorageService gcsService;
        try {
            gcsService = new GcsStorageService();
        } catch (Exception e) {
            System.err.println("\n[ERRO DE INICIALIZAÇÃO / AUTENTICAÇÃO]");
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        ProdutoService produtoService = new ProdutoService(gcsService);

        // Inicia o Servidor Web para a Interface Gráfica do E-commerce
        ServidorWebEcommerce servidorWeb = new ServidorWebEcommerce(PORTA_WEB, gcsService, produtoService);
        try {
            servidorWeb.iniciar();
            String urlWeb = "http://localhost:" + PORTA_WEB;
            System.out.println("\n============================================================");
            System.out.println("  INTERFACE WEB DO E-COMMERCE NO AR: " + urlWeb);
            System.out.println("  Abra no navegador para usar a loja virtual visualmente!");
            System.out.println("============================================================");
            abrirNavegadorAutomaticamente(urlWeb);
        } catch (IOException e) {
            System.err.println("\n[AVISO] Não foi possível iniciar a porta " + PORTA_WEB + " (" + e.getMessage() + "). O console continuará funcionando.");
        }

        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            boolean executando = true;
            while (executando) {
                exibirMenu();
                if (!scanner.hasNextLine()) {
                    // Caso o console não tenha entrada interativa, mantém o servidor web ativo
                    try {
                        Thread.currentThread().join();
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    break;
                }
                String opcao = scanner.nextLine().trim();

                try {
                    switch (opcao) {
                        case "1" -> fluxoCadastrarProduto(scanner, produtoService);
                        case "2" -> fluxoUploadImagem(scanner, produtoService);
                        case "3" -> fluxoListarProdutos(produtoService);
                        case "4" -> fluxoConsultarProduto(scanner, produtoService);
                        case "5" -> fluxoDemonstrarDownloadOuLeitura(scanner, gcsService);
                        case "6" -> fluxoExcluirProduto(scanner, produtoService);
                        case "0" -> {
                            System.out.println("\nEncerrando o servidor web e o sistema. Obrigado!");
                            servidorWeb.parar();
                            executando = false;
                        }
                        default -> System.out.println("\n[!] Opção inválida. Escolha um número de 0 a 6.");
                    }
                } catch (Exception e) {
                    System.err.println("\n[ERRO NA OPERAÇÃO] " + e.getMessage());
                }
            }
        }
    }

    private static void exibirCabecalho() {
        System.out.println("============================================================");
        System.out.println("   E-COMMERCE CLOUD CATALOG - GOOGLE CLOUD STORAGE (GCS)    ");
        System.out.println("   Fatec Franca - DSM | Projeto: " + GcsStorageService.PROJECT_ID);
        System.out.println("   Bucket: " + GcsStorageService.BUCKET_NAME + " (grupo-i)");
        System.out.println("   Integrantes: Hugo Rodrigues, Pablo Nóbrega, Arthur Ferreira");
        System.out.println("============================================================");
    }

    private static void exibirMenu() {
        System.out.println("\n---------------- MENU CONSOLE (OU USE A WEB EM http://localhost:" + PORTA_WEB + ") ----------------");
        System.out.println("1. Cadastrar produto (gera JSON e envia para o bucket)");
        System.out.println("2. Upload de imagem de produto para o bucket");
        System.out.println("3. Listar produtos cadastrados no bucket");
        System.out.println("4. Consultar detalhes de um produto específico");
        System.out.println("5. Demonstrar download ou leitura direta de objeto do GCS");
        System.out.println("6. Excluir produto do bucket (com confirmação)");
        System.out.println("0. Sair");
        System.out.print("Escolha uma opção: ");
    }

    private static void fluxoCadastrarProduto(Scanner scanner, ProdutoService produtoService) {
        System.out.println("\n>>> [1] CADASTRAR NOVO PRODUTO");
        String nome = ValidadorEntrada.lerTextoObrigatorio(scanner, "Nome do produto: ", 3, 120);
        String categoria = ValidadorEntrada.lerTextoObrigatorio(scanner, "Categoria (ex: Eletrônicos, Vestuário): ", 3, 60);
        BigDecimal preco = ValidadorEntrada.lerPrecoValido(scanner, "Preço (R$): ");
        String descricao = ValidadorEntrada.lerTextoObrigatorio(scanner, "Descrição do produto: ", 5, 500);

        System.out.println("Enviando arquivo JSON para o Google Cloud Storage...");
        Produto cadastrado = produtoService.cadastrarProduto(nome, descricao, preco, categoria);

        System.out.println("\n[SUCESSO] Produto cadastrado e armazenado no GCS!");
        System.out.println(cadastrado);
    }

    private static void fluxoUploadImagem(Scanner scanner, ProdutoService produtoService) {
        System.out.println("\n>>> [2] UPLOAD DE IMAGEM DE PRODUTO");
        String idInput = ValidadorEntrada.lerTextoObrigatorio(
                scanner,
                "Informe o ID do produto (ex: produto-001 ou 1): ",
                1, 40
        );

        String idNormalizado = produtoService.normalizarIdProduto(idInput);
        Optional<Produto> existente = produtoService.buscarProdutoPorId(idNormalizado);
        if (existente.isEmpty()) {
            System.out.println("[!] O produto '" + idNormalizado + "' não existe no bucket. Cadastre-o primeiro na opção 1.");
            return;
        }

        System.out.println("Produto localizado: " + existente.get().getNome());
        System.out.println("Dica: você pode usar a imagem de teste incluída no projeto: amostras/produto-exemplo.png");
        Path arquivoImagem = ValidadorEntrada.lerCaminhoArquivoExistente(
                scanner,
                "Caminho local da imagem (.png, .jpg, .webp): "
        );

        System.out.println("Enviando imagem para o bucket e atualizando metadados do produto...");
        Produto atualizado = produtoService.anexarImagemAoProduto(idNormalizado, arquivoImagem);

        System.out.println("\n[SUCESSO] Imagem enviada e vinculada ao produto!");
        System.out.println("Objeto criado no bucket : " + atualizado.getCaminhoObjetoImagem());
        System.out.println("URL pública da imagem   : " + atualizado.getUrlPublicaImagem());
    }

    private static void fluxoListarProdutos(ProdutoService produtoService) {
        System.out.println("\n>>> [3] LISTANDO PRODUTOS NO BUCKET (" + GcsStorageService.BUCKET_NAME + ")");
        List<Produto> produtos = produtoService.listarProdutos();

        if (produtos.isEmpty()) {
            System.out.println("Nenhum produto encontrado em 'produtos/' no bucket.");
            return;
        }

        System.out.printf("Total de produtos cadastrados: %d%n", produtos.size());
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("%-13s | %-28s | %-15s | %-12s | %s%n",
                "ID", "NOME", "CATEGORIA", "PREÇO", "IMAGEM");
        System.out.println("--------------------------------------------------------------------------------");

        for (Produto p : produtos) {
            String statusImagem = (p.getCaminhoObjetoImagem() != null) ? p.getCaminhoObjetoImagem() : "(sem imagem)";
            System.out.printf("%-13s | %-28s | %-15s | R$ %-9s | %s%n",
                    p.getId(),
                    truncar(p.getNome(), 28),
                    truncar(p.getCategoria(), 15),
                    p.getPreco() != null ? p.getPreco().toPlainString() : "0.00",
                    statusImagem
            );
        }
        System.out.println("--------------------------------------------------------------------------------");
    }

    private static void fluxoConsultarProduto(Scanner scanner, ProdutoService produtoService) {
        System.out.println("\n>>> [4] CONSULTAR PRODUTO ESPECÍFICO");
        String idInput = ValidadorEntrada.lerTextoObrigatorio(
                scanner,
                "Informe o ID do produto (ex: produto-001 ou 1): ",
                1, 40
        );

        Optional<Produto> produtoOpt = produtoService.buscarProdutoPorId(idInput);
        if (produtoOpt.isEmpty()) {
            System.out.println("[!] Produto não encontrado no bucket.");
            return;
        }

        System.out.println("\nDados recuperados do Google Cloud Storage:");
        System.out.println(produtoOpt.get());
    }

    private static void fluxoDemonstrarDownloadOuLeitura(Scanner scanner, GcsStorageService gcsService) {
        System.out.println("\n>>> [5] DEMONSTRAR DOWNLOAD OU LEITURA DE OBJETO DO GCS");
        System.out.println("Objetos atualmente disponíveis no bucket:");
        List<String> objetosProdutos = gcsService.listarObjetosPorPrefixo(ProdutoService.PREFIXO_PRODUTOS);
        List<String> objetosImagens = gcsService.listarObjetosPorPrefixo(ProdutoService.PREFIXO_IMAGENS);

        if (objetosProdutos.isEmpty() && objetosImagens.isEmpty()) {
            System.out.println("  (O bucket não possui objetos em 'produtos/' ou 'imagens/' no momento)");
            return;
        }
        objetosProdutos.forEach(obj -> System.out.println("  - " + obj));
        objetosImagens.forEach(obj -> System.out.println("  - " + obj));

        String caminhoObjeto = ValidadorEntrada.lerTextoObrigatorio(
                scanner,
                "\nInforme o caminho exato do objeto no bucket (ex: produtos/produto-001.json): ",
                3, 200
        );

        if (!gcsService.objetoExiste(caminhoObjeto)) {
            System.out.println("[!] O objeto '" + caminhoObjeto + "' não existe no bucket '" + GcsStorageService.BUCKET_NAME + "'.");
            return;
        }

        System.out.println("URL pública correspondente: " + gcsService.gerarUrlPublica(caminhoObjeto));
        System.out.println("Escolha o modo de demonstração:");
        System.out.println("  [1] Ler e exibir o conteúdo bruto (UTF-8) diretamente no console");
        System.out.println("  [2] Fazer download do objeto e salvá-lo localmente na pasta 'downloads/'");
        System.out.print("Opção (1 ou 2): ");
        String modo = scanner.nextLine().trim();

        if ("1".equals(modo)) {
            Optional<String> conteudo = gcsService.lerObjetoComoTexto(caminhoObjeto);
            if (conteudo.isPresent()) {
                System.out.println("\n--- INÍCIO DO CONTEÚDO DO OBJETO (" + caminhoObjeto + ") ---");
                System.out.println(conteudo.get());
                System.out.println("--- FIM DO CONTEÚDO DO OBJETO ---");
            }
        } else if ("2".equals(modo)) {
            String nomeArquivoLocal = Path.of(caminhoObjeto).getFileName().toString();
            Path destino = Path.of("downloads", nomeArquivoLocal);
            boolean baixado = gcsService.baixarObjetoParaArquivo(caminhoObjeto, destino);
            if (baixado) {
                System.out.println("\n[SUCESSO] Objeto baixado do GCS com sucesso!");
                System.out.println("Arquivo salvo localmente em: " + destino.toAbsolutePath());
            }
        } else {
            System.out.println("[!] Modo inválido. Escolha 1 ou 2.");
        }
    }

    private static void fluxoExcluirProduto(Scanner scanner, ProdutoService produtoService) {
        System.out.println("\n>>> [6] EXCLUIR PRODUTO DO BUCKET");
        String idInput = ValidadorEntrada.lerTextoObrigatorio(
                scanner,
                "Informe o ID do produto que deseja excluir (ex: produto-001 ou 1): ",
                1, 40
        );

        String idNormalizado = produtoService.normalizarIdProduto(idInput);
        Optional<Produto> produtoOpt = produtoService.buscarProdutoPorId(idNormalizado);

        if (produtoOpt.isEmpty()) {
            System.out.println("[!] Produto '" + idNormalizado + "' não encontrado no bucket.");
            return;
        }

        Produto produto = produtoOpt.get();
        System.out.println("\nProduto selecionado para exclusão:");
        System.out.println(produto);

        boolean confirmado = ValidadorEntrada.confirmarAcao(
                scanner,
                "Tem certeza que deseja excluir permanentemente este produto (e sua imagem, se houver) do GCS?"
        );

        if (!confirmado) {
            System.out.println("Operação cancelada pelo usuário. Nenhum arquivo foi removido.");
            return;
        }

        boolean removido = produtoService.excluirProduto(idNormalizado);
        if (removido) {
            System.out.println("\n[SUCESSO] Produto '" + idNormalizado + "' removido do bucket com sucesso!");
        } else {
            System.out.println("\n[!] Não foi possível remover o produto.");
        }
    }

    private static void abrirNavegadorAutomaticamente(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ignored) {
            // Caso o ambiente não permita abrir navegador automaticamente, o link continua visível no console
        }
    }

    private static String truncar(String texto, int limite) {
        if (texto == null) {
            return "";
        }
        if (texto.length() <= limite) {
            return texto;
        }
        return texto.substring(0, limite - 3) + "...";
    }

    /**
     * Cria automaticamente uma imagem PNG ilustrativa em 'amostras/produto-exemplo.png'
     * caso não exista, facilitando a demonstração prática do upload de imagem em aula.
     */
    private static void garantirImagemAmostraLocal() {
        try {
            Path caminhoAmostra = Path.of("amostras", "produto-exemplo.png");
            Files.createDirectories(caminhoAmostra.getParent());

            int largura = 600;
            int altura = 380;
            BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g.setPaint(new GradientPaint(0, 0, new Color(15, 23, 42), largura, altura, new Color(37, 99, 235)));
            g.fillRect(0, 0, largura, altura);

            g.setColor(new Color(255, 255, 255, 35));
            g.fillRoundRect(40, 40, largura - 80, altura - 80, 24, 24);

            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 30));
            g.drawString("FATECSHOP CLOUD", 155, 155);

            g.setColor(new Color(147, 197, 253));
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            g.drawString("Google Cloud Storage • Grupo I", 150, 195);

            g.setColor(new Color(226, 232, 240));
            g.setFont(new Font("Monospaced", Font.PLAIN, 16));
            g.drawString("Bucket: fatec-2026-grupo-i", 170, 240);

            g.dispose();
            ImageIO.write(img, "png", caminhoAmostra.toFile());
        } catch (IOException ignored) {
            // Não impede a execução caso o diretório local seja somente leitura
        }
    }
}
