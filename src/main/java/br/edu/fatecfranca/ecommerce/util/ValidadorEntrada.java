package br.edu.fatecfranca.ecommerce.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * Utilitário para leitura e validação consistente dos dados de entrada no console.
 */
public final class ValidadorEntrada {

    private ValidadorEntrada() {
    }

    /**
     * Lê um campo de texto obrigatório, garantindo tamanho mínimo e máximo.
     */
    public static String lerTextoObrigatorio(Scanner scanner, String rotulo, int tamanhoMinimo, int tamanhoMaximo) {
        while (true) {
            System.out.print(rotulo);
            String valor = scanner.nextLine().trim();
            if (valor.isEmpty()) {
                System.out.println("  [!] Este campo é obrigatório. Por favor, preencha o valor.");
                continue;
            }
            if (valor.length() < tamanhoMinimo) {
                System.out.printf("  [!] O valor deve ter pelo menos %d caracteres.%n", tamanhoMinimo);
                continue;
            }
            if (valor.length() > tamanhoMaximo) {
                System.out.printf("  [!] O valor não pode ultrapassar %d caracteres.%n", tamanhoMaximo);
                continue;
            }
            return valor;
        }
    }

    /**
     * Lê e valida um preço monetário positivo utilizando BigDecimal com 2 casas decimais.
     * Aceita vírgula ou ponto como separador decimal (ex: "149,90" ou "149.90").
     */
    public static BigDecimal lerPrecoValido(Scanner scanner, String rotulo) {
        while (true) {
            System.out.print(rotulo);
            String entrada = scanner.nextLine().trim().replace(",", ".");
            if (entrada.isEmpty()) {
                System.out.println("  [!] O preço é obrigatório.");
                continue;
            }
            try {
                BigDecimal preco = new BigDecimal(entrada).setScale(2, RoundingMode.HALF_UP);
                if (preco.compareTo(BigDecimal.ZERO) <= 0) {
                    System.out.println("  [!] O preço deve ser maior que zero (ex: 99.90).");
                    continue;
                }
                return preco;
            } catch (NumberFormatException e) {
                System.out.println("  [!] Formato de preço inválido. Digite um valor numérico positivo (ex: 129.90 ou 129,90).");
            }
        }
    }

    /**
     * Lê um caminho de arquivo local e valida se ele realmente existe no disco.
     */
    public static Path lerCaminhoArquivoExistente(Scanner scanner, String rotulo) {
        while (true) {
            System.out.print(rotulo);
            String entrada = scanner.nextLine().trim();
            // Remove aspas caso o usuário arraste o arquivo ou copie o caminho com aspas no Windows
            if (entrada.startsWith("\"") && entrada.endsWith("\"") && entrada.length() > 1) {
                entrada = entrada.substring(1, entrada.length() - 1);
            }
            if (entrada.isEmpty()) {
                System.out.println("  [!] Informe o caminho do arquivo.");
                continue;
            }
            try {
                Path path = Path.of(entrada);
                if (!Files.exists(path) || !Files.isRegularFile(path)) {
                    System.out.println("  [!] Arquivo não encontrado em: " + path.toAbsolutePath());
                    continue;
                }
                return path;
            } catch (InvalidPathException e) {
                System.out.println("  [!] Caminho de arquivo inválido: " + e.getMessage());
            }
        }
    }

    /**
     * Solicita confirmação explícita (S/N) antes de operações destrutivas.
     */
    public static boolean confirmarAcao(Scanner scanner, String mensagem) {
        System.out.print(mensagem + " (S/N): ");
        String resposta = scanner.nextLine().trim();
        return resposta.equalsIgnoreCase("S") || resposta.equalsIgnoreCase("SIM");
    }
}
