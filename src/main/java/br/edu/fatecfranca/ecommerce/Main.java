package br.edu.fatecfranca.ecommerce;

import br.edu.fatecfranca.ecommerce.database.DatabaseService;
import br.edu.fatecfranca.ecommerce.model.Produto;
import br.edu.fatecfranca.ecommerce.model.ProdutoImagem;
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
 * Ponto de entrada da aplicação de gerenciamento de produtos de E-commerce.
 *
 * Conforme exigências do projeto:
 * - Banco de Dados Local SQLite (ecommerce.db) para entidades, metadados e marcação de capa.
 * - Google Cloud Storage (bucket fatec-2026-grupo-i) exclusivamente para arquivos de imagem.
 * - Suporte a N imagens por produto com seleção de capa e exclusão individual.
 *
 * Fatec Franca - Desenvolvimento de Software Multiplataforma
 * Grupo I: Hugo de Castro Rodrigues, Pablo Miguel Sousa Nóbrega, Arthur Rodrigues Ferreira
 */
public class Main {

    public static final int PORTA_WEB = 8080;

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        garantirImagemAmostraLocal();
        exibirCabecalho();

        GcsStorageService gcsService;
        try {
            gcsService = new GcsStorageService();
        } catch (Exception e) {
            System.err.println("\n[ERRO DE INICIALIZAÇÃO / AUTENTICAÇÃO GCS]");
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        DatabaseService databaseService = new DatabaseService();
        ProdutoService produtoService = new ProdutoService(gcsService, databaseService);

        // Inicia o Servidor Web para a Interface Gráfica do E-commerce
        ServidorWebEcommerce servidorWeb = new ServidorWebEcommerce(PORTA_WEB, gcsService, produtoService);
        try {
            servidorWeb.iniciar();
            String urlWeb = "http://localhost:" + PORTA_WEB;
            System.out.println("\n============================================================");
            System.out.println("  INTERFACE WEB DO E-COMMERCE NO AR: " + urlWeb);
            System.out.println("  Banco de dados : SQLite local (ecommerce.db)");
            System.out.println("  Armazenamento  : Google Cloud Storage (fatec-2026-grupo-i)");
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
                        case "5" -> fluxoGerenciarImagens(scanner, produtoService);
                        case "6" -> fluxoGerarCargaDemonstracao(produtoService);
                        case "7" -> fluxoDemonstrarDownloadOuLeitura(scanner, gcsService);
                        case "8" -> fluxoExcluirProduto(scanner, produtoService);
                        case "0" -> {
                            System.out.println("\nEncerrando o servidor web e o sistema. Obrigado!");
                            servidorWeb.parar();
                            executando = false;
                        }
                        default -> System.out.println("\n[!] Opção inválida. Escolha um número de 0 a 8.");
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
        System.out.println("   Banco Local: SQLite (ecommerce.db)  |  Múltiplas Fotos/Capa");
        System.out.println("   Projeto GCS: " + GcsStorageService.PROJECT_ID + "  |  Bucket: " + GcsStorageService.BUCKET_NAME);
        System.out.println("   Integrantes: Hugo Rodrigues, Pablo Nóbrega, Arthur Ferreira");
        System.out.println("============================================================");
    }

    private static void exibirMenu() {
        System.out.println("\n---------------- MENU CONSOLE (OU ACESSE http://localhost:" + PORTA_WEB + ") ----------------");
        System.out.println("1. Cadastrar produto (armazena no SQLite local)");
        System.out.println("2. Upload de imagem para produto no GCS (suporta N imagens e marcação de capa)");
        System.out.println("3. Listar produtos do catálogo (dados SQLite + miniatura da capa no GCS)");
        System.out.println("4. Consultar produto (detalhes, capa e galeria com todas as fotos)");
        System.out.println("5. Gerenciar imagens de um produto (definir capa, baixar ou apagar imagem)");
        System.out.println("6. Gerar Carga de Demonstração (10 produtos com 3 fotos cada = 30 fotos no GCS)");
        System.out.println("7. Demonstrar download ou leitura de objeto do GCS");
        System.out.println("8. Excluir produto completo (remove do SQLite e apaga fotos do GCS)");
        System.out.println("0. Sair");
        System.out.print("Escolha uma opção: ");
    }

    private static void fluxoCadastrarProduto(Scanner scanner, ProdutoService produtoService) {
        System.out.println("\n>>> [1] CADASTRAR NOVO PRODUTO (BANCO LOCAL SQLITE)");
        String nome = ValidadorEntrada.lerTextoObrigatorio(scanner, "Nome do produto: ", 3, 120);
        String categoria = ValidadorEntrada.lerTextoObrigatorio(scanner, "Categoria (ex: Eletrônicos, Informática): ", 2, 60);
        BigDecimal preco = ValidadorEntrada.lerPrecoValido(scanner, "Preço (R$): ");
        String descricao = ValidadorEntrada.lerTextoObrigatorio(scanner, "Descrição do produto: ", 5, 500);

        Produto cadastrado = produtoService.cadastrarProduto(nome, descricao, preco, categoria);
        System.out.println("\n[SUCESSO] Produto salvo no banco de dados local SQLite (ecommerce.db)!");
        System.out.println(cadastrado);
    }

    private static void fluxoUploadImagem(Scanner scanner, ProdutoService produtoService) {
        System.out.println("\n>>> [2] UPLOAD DE IMAGEM PARA PRODUTO NO BUCKET GCS");
        String idInput = ValidadorEntrada.lerTextoObrigatorio(
                scanner,
                "Informe o ID do produto (ex: produto-001 ou 1): ",
                1, 40
        );

        String idNormalizado = produtoService.normalizarIdProduto(idInput);
        Optional<Produto> existente = produtoService.buscarProdutoPorId(idNormalizado);
        if (existente.isEmpty()) {
            System.out.println("[!] O produto '" + idNormalizado + "' não existe no SQLite. Cadastre-o primeiro na opção 1.");
            return;
        }

        Produto prod = existente.get();
        System.out.println("Produto localizado: " + prod.getNome() + " (Fotos atuais: " + prod.getTotalImagens() + ")");
        System.out.println("Dica: você pode usar a imagem de teste em: amostras/produto-exemplo.png");
        Path arquivoImagem = ValidadorEntrada.lerCaminhoArquivoExistente(
                scanner,
                "Caminho local da imagem (.png, .jpg, .webp): "
        );

        boolean definirComoCapa = prod.getTotalImagens() == 0 || ValidadorEntrada.confirmarAcao(
                scanner,
                "Deseja definir esta foto como a IMAGEM PRINCIPAL (capa do catálogo)?"
        );

        System.out.println("Enviando imagem para o bucket 'fatec-2026-grupo-i' via stream...");
        ProdutoImagem imagemCriada = produtoService.anexarImagemAoProduto(idNormalizado, arquivoImagem, definirComoCapa);

        System.out.println("\n[SUCESSO] Imagem enviada para o GCS e vinculada ao produto no SQLite!");
        System.out.println("ID da Imagem       : " + imagemCriada.getId());
        System.out.println("Capa do Catálogo   : " + (imagemCriada.isEhPrincipal() ? "SIM (Principal)" : "Não"));
        System.out.println("Objeto no GCS      : " + imagemCriada.getCaminhoObjeto());
        System.out.println("URL Pública GCS    : " + imagemCriada.getUrlPublica());
    }

    private static void fluxoListarProdutos(ProdutoService produtoService) {
        System.out.println("\n>>> [3] LISTANDO CATÁLOGO DE PRODUTOS (SQLITE + GCS)");
        List<Produto> produtos = produtoService.listarProdutos();

        if (produtos.isEmpty()) {
            System.out.println("Nenhum produto cadastrado no banco SQLite local. Utilize a opção 1 ou 6 (Carga Demo).");
            return;
        }

        System.out.printf("Total de produtos cadastrados: %d%n", produtos.size());
        System.out.println("----------------------------------------------------------------------------------------------------------------");
        System.out.printf("%-13s | %-28s | %-14s | %-11s | %-7s | %s%n",
                "ID", "NOME", "CATEGORIA", "PREÇO", "FOTOS", "IMAGEM PRINCIPAL (CAPA GCS)");
        System.out.println("----------------------------------------------------------------------------------------------------------------");

        for (Produto p : produtos) {
            String capa = p.getUrlImagemPrincipal() != null ? p.getCaminhoImagemPrincipal() : "(sem imagem)";
            System.out.printf("%-13s | %-28s | %-14s | R$ %-8s | %-7d | %s%n",
                    p.getId(),
                    truncar(p.getNome(), 28),
                    truncar(p.getCategoria(), 14),
                    p.getPreco() != null ? p.getPreco().toPlainString() : "0.00",
                    p.getTotalImagens(),
                    capa
            );
        }
        System.out.println("----------------------------------------------------------------------------------------------------------------");
    }

    private static void fluxoConsultarProduto(Scanner scanner, ProdutoService produtoService) {
        System.out.println("\n>>> [4] CONSULTAR PRODUTO E GALERIA DE IMAGENS");
        String idInput = ValidadorEntrada.lerTextoObrigatorio(
                scanner,
                "Informe o ID do produto (ex: produto-001 ou 1): ",
                1, 40
        );

        Optional<Produto> produtoOpt = produtoService.buscarProdutoPorId(idInput);
        if (produtoOpt.isEmpty()) {
            System.out.println("[!] Produto não encontrado no banco de dados.");
            return;
        }

        Produto p = produtoOpt.get();
        System.out.println("\nDados do Produto (SQLite local):");
        System.out.println(p);

        System.out.println("\nGaleria de Imagens vinculadas (" + p.getTotalImagens() + " fotos no GCS):");
        if (p.getImagens().isEmpty()) {
            System.out.println("  (Nenhuma imagem cadastrada para este produto)");
        } else {
            for (ProdutoImagem img : p.getImagens()) {
                String statusCapa = img.isEhPrincipal() ? "[★ CAPA PRINCIPAL]" : "[  GALERIA      ]";
                System.out.printf("  #%d %s %s -> %s%n",
                        img.getId(), statusCapa, img.getCaminhoObjeto(), img.getUrlPublica());
            }
        }
    }

    private static void fluxoGerenciarImagens(Scanner scanner, ProdutoService produtoService) {
        System.out.println("\n>>> [5] GERENCIAR IMAGENS DE UM PRODUTO");
        String idInput = ValidadorEntrada.lerTextoObrigatorio(
                scanner,
                "Informe o ID do produto (ex: produto-001 ou 1): ",
                1, 40
        );

        String idNormalizado = produtoService.normalizarIdProduto(idInput);
        Optional<Produto> produtoOpt = produtoService.buscarProdutoPorId(idNormalizado);
        if (produtoOpt.isEmpty()) {
            System.out.println("[!] Produto não encontrado.");
            return;
        }

        Produto prod = produtoOpt.get();
        if (prod.getImagens().isEmpty()) {
            System.out.println("[!] O produto '" + idNormalizado + "' não possui fotos cadastradas.");
            return;
        }

        System.out.println("\nFotos disponíveis para " + prod.getNome() + ":");
        for (ProdutoImagem img : prod.getImagens()) {
            System.out.printf("  [ID %d] %s %s%n",
                    img.getId(),
                    img.isEhPrincipal() ? "★ CAPA" : "  FOTO",
                    img.getCaminhoObjeto()
            );
        }

        System.out.println("\nO que deseja fazer?");
        System.out.println("  [1] Definir imagem como Capa Principal");
        System.out.println("  [2] Baixar imagem localmente");
        System.out.println("  [3] Apagar imagem individualmente (remove do GCS e do SQLite)");
        System.out.println("  [0] Voltar");
        System.out.print("Opção: ");
        String subOpcao = scanner.nextLine().trim();

        if ("1".equals(subOpcao)) {
            System.out.print("Informe o ID numérico da imagem que será a nova Capa: ");
            long idImg = Long.parseLong(scanner.nextLine().trim());
            boolean ok = produtoService.definirImagemPrincipal(idNormalizado, idImg);
            if (ok) {
                System.out.println("[SUCESSO] Nova imagem principal (capa) definida com sucesso!");
            } else {
                System.out.println("[!] Não foi possível atualizar a capa.");
            }
        } else if ("2".equals(subOpcao)) {
            System.out.print("Informe o ID numérico da imagem que deseja baixar: ");
            long idImg = Long.parseLong(scanner.nextLine().trim());
            prod.getImagens().stream().filter(i -> i.getId().equals(idImg)).findFirst().ifPresentOrElse(img -> {
                String nomeArquivoLocal = Path.of(img.getCaminhoObjeto()).getFileName().toString();
                Path destino = Path.of("downloads", nomeArquivoLocal);
                boolean baixou = produtoService.getGcsService().baixarObjetoParaArquivo(img.getCaminhoObjeto(), destino);
                if (baixou) {
                    System.out.println("[SUCESSO] Imagem baixada em: " + destino.toAbsolutePath());
                } else {
                    System.out.println("[!] Erro ao baixar imagem do GCS.");
                }
            }, () -> System.out.println("[!] Imagem não encontrada."));
        } else if ("3".equals(subOpcao)) {
            System.out.print("Informe o ID numérico da imagem que deseja EXCLUIR: ");
            long idImg = Long.parseLong(scanner.nextLine().trim());
            boolean confirm = ValidadorEntrada.confirmarAcao(scanner, "Tem certeza que deseja apagar esta foto do GCS e do banco?");
            if (confirm) {
                boolean ok = produtoService.excluirImagemIndividual(idNormalizado, idImg);
                if (ok) {
                    System.out.println("[SUCESSO] Imagem apagada com sucesso do GCS e do banco SQLite!");
                } else {
                    System.out.println("[!] Erro ao excluir imagem.");
                }
            }
        }
    }

    private static void fluxoGerarCargaDemonstracao(ProdutoService produtoService) {
        System.out.println("\n>>> [6] GERAR CARGA DE DEMONSTRAÇÃO (10 PRODUTOS x 3 IMAGENS)");
        System.out.println("Iniciando geração de 10 produtos de e-commerce e envio de 30 imagens reais para o GCS...");
        System.out.println("Bucket de destino: " + GcsStorageService.BUCKET_NAME);

        try {
            int fotosEnviadas = produtoService.popularCargaDemonstracao();
            System.out.println("\n[PARABÉNS] Carga de demonstração concluída com sucesso!");
            System.out.println("- 10 produtos cadastrados no SQLite local.");
            System.out.println("- " + fotosEnviadas + " imagens de alta qualidade geradas e enviadas para o bucket GCS.");
            System.out.println("- Capas do catálogo e galerias devidamente configuradas.");
            System.out.println("Abra a interface Web (http://localhost:" + PORTA_WEB + ") para visualizar os produtos e fotos!");
        } catch (Exception e) {
            System.err.println("\n[!] Falha durante a carga de demonstração: " + e.getMessage());
        }
    }

    private static void fluxoDemonstrarDownloadOuLeitura(Scanner scanner, GcsStorageService gcsService) {
        System.out.println("\n>>> [7] DEMONSTRAR DOWNLOAD OU LEITURA DIRETA DO GCS");
        System.out.println("Imagens atualmente no bucket sob 'imagens/':");
        List<String> objetosImagens = gcsService.listarObjetosPorPrefixo(ProdutoService.PREFIXO_IMAGENS);

        if (objetosImagens.isEmpty()) {
            System.out.println("  (Nenhum objeto em 'imagens/' no momento. Cadastre produtos ou execute a opção 6).");
            return;
        }
        objetosImagens.stream().limit(20).forEach(obj -> System.out.println("  - " + obj));
        if (objetosImagens.size() > 20) {
            System.out.println("  ... e mais " + (objetosImagens.size() - 20) + " imagens.");
        }

        String caminhoObjeto = ValidadorEntrada.lerTextoObrigatorio(
                scanner,
                "\nInforme o caminho do objeto no bucket: ",
                3, 200
        );

        if (!gcsService.objetoExiste(caminhoObjeto)) {
            System.out.println("[!] O objeto não existe no bucket '" + GcsStorageService.BUCKET_NAME + "'.");
            return;
        }

        System.out.println("URL pública direta: " + gcsService.gerarUrlPublica(caminhoObjeto));
        String nomeArquivoLocal = Path.of(caminhoObjeto).getFileName().toString();
        Path destino = Path.of("downloads", nomeArquivoLocal);
        boolean baixado = gcsService.baixarObjetoParaArquivo(caminhoObjeto, destino);
        if (baixado) {
            System.out.println("\n[SUCESSO] Imagem baixada do GCS com sucesso!");
            System.out.println("Arquivo salvo em: " + destino.toAbsolutePath());
        }
    }

    private static void fluxoExcluirProduto(Scanner scanner, ProdutoService produtoService) {
        System.out.println("\n>>> [8] EXCLUIR PRODUTO COMPLETO");
        String idInput = ValidadorEntrada.lerTextoObrigatorio(
                scanner,
                "Informe o ID do produto que deseja excluir: ",
                1, 40
        );

        String idNormalizado = produtoService.normalizarIdProduto(idInput);
        Optional<Produto> produtoOpt = produtoService.buscarProdutoPorId(idNormalizado);

        if (produtoOpt.isEmpty()) {
            System.out.println("[!] Produto '" + idNormalizado + "' não encontrado.");
            return;
        }

        Produto produto = produtoOpt.get();
        System.out.println("\nProduto selecionado para exclusão:");
        System.out.println(produto);

        boolean confirmado = ValidadorEntrada.confirmarAcao(
                scanner,
                "Tem certeza que deseja excluir o produto e TODAS as suas " + produto.getTotalImagens() + " foto(s) do GCS e do banco?"
        );

        if (!confirmado) {
            System.out.println("Operação cancelada.");
            return;
        }

        boolean removido = produtoService.excluirProduto(idNormalizado);
        if (removido) {
            System.out.println("\n[SUCESSO] Produto e fotos removidos com sucesso!");
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
        }
    }

    private static String truncar(String texto, int limite) {
        if (texto == null) return "";
        if (texto.length() <= limite) return texto;
        return texto.substring(0, limite - 3) + "...";
    }

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
        }
    }
}
