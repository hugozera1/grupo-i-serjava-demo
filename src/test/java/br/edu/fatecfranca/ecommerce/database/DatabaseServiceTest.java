package br.edu.fatecfranca.ecommerce.database;

import br.edu.fatecfranca.ecommerce.model.Produto;
import br.edu.fatecfranca.ecommerce.model.ProdutoImagem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseServiceTest {

    private static final String BANCO_TESTE = "target/test-ecommerce.db";
    private DatabaseService databaseService;

    @BeforeEach
    void setUp() throws IOException {
        Files.deleteIfExists(Path.of(BANCO_TESTE));
        databaseService = new DatabaseService(BANCO_TESTE);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(Path.of(BANCO_TESTE));
    }

    @Test
    @DisplayName("Deve cadastrar produto e recuperar corretamente do banco SQLite local")
    void deveSalvarEConsultarProduto() {
        Produto produto = new Produto("produto-001", "Teclado Mecânico RGB", "Switches Hot-Swap", new BigDecimal("350.00"), "Periféricos");
        databaseService.salvarProduto(produto);

        Optional<Produto> recuperado = databaseService.buscarProdutoPorId("produto-001");
        assertTrue(recuperado.isPresent());
        assertEquals("Teclado Mecânico RGB", recuperado.get().getNome());
        assertEquals("Periféricos", recuperado.get().getCategoria());
        assertEquals(new BigDecimal("350.00"), recuperado.get().getPreco());
        assertEquals(0, recuperado.get().getTotalImagens());
    }

    @Test
    @DisplayName("Deve permitir N imagens por produto e definir a primeira imagem como capa automaticamente")
    void deveGerenciarMultiplasImagensECapaPadrao() {
        Produto produto = new Produto("produto-001", "Monitor 144Hz", "Monitor Gamer 24 pol", new BigDecimal("999.00"), "Monitores");
        databaseService.salvarProduto(produto);

        // Inserir 1ª foto
        ProdutoImagem img1 = new ProdutoImagem("produto-001", "imagens/produto-001/foto1.png", "https://storage.googleapis.com/.../foto1.png", "foto1.png", false);
        databaseService.inserirImagem(img1);

        // Inserir 2ª foto
        ProdutoImagem img2 = new ProdutoImagem("produto-001", "imagens/produto-001/foto2.png", "https://storage.googleapis.com/.../foto2.png", "foto2.png", false);
        databaseService.inserirImagem(img2);

        // Inserir 3ª foto
        ProdutoImagem img3 = new ProdutoImagem("produto-001", "imagens/produto-001/foto3.png", "https://storage.googleapis.com/.../foto3.png", "foto3.png", false);
        databaseService.inserirImagem(img3);

        List<ProdutoImagem> imagens = databaseService.listarImagensDoProduto("produto-001");
        assertEquals(3, imagens.size());

        // A primeira deve ter sido definida como principal automaticamente
        Produto produtoComImagens = databaseService.buscarProdutoPorId("produto-001").orElseThrow();
        assertEquals(3, produtoComImagens.getTotalImagens());
        assertTrue(produtoComImagens.obterImagemPrincipal().isPresent());
        assertEquals("imagens/produto-001/foto1.png", produtoComImagens.obterImagemPrincipal().get().getCaminhoObjeto());
    }

    @Test
    @DisplayName("Deve permitir alternar a imagem principal (capa) entre as imagens existentes")
    void deveAlternarImagemPrincipal() {
        Produto produto = new Produto("produto-001", "Mouse Gamer", "Sensor PixArt", new BigDecimal("199.90"), "Periféricos");
        databaseService.salvarProduto(produto);

        ProdutoImagem img1 = databaseService.inserirImagem(new ProdutoImagem("produto-001", "imagens/foto1.png", "url1", "foto1.png", true));
        ProdutoImagem img2 = databaseService.inserirImagem(new ProdutoImagem("produto-001", "imagens/foto2.png", "url2", "foto2.png", false));

        // Define a segunda como capa
        boolean alterado = databaseService.definirImagemPrincipal("produto-001", img2.getId());
        assertTrue(alterado);

        Produto prodAtualizado = databaseService.buscarProdutoPorId("produto-001").orElseThrow();
        assertEquals(img2.getId(), prodAtualizado.obterImagemPrincipal().orElseThrow().getId());
    }

    @Test
    @DisplayName("Deve permitir apagar uma imagem individualmente do SQLite")
    void deveExcluirImagemIndividualmente() {
        Produto produto = new Produto("produto-001", "Headset 7.1", "Áudio espacial", new BigDecimal("299.00"), "Áudio");
        databaseService.salvarProduto(produto);

        ProdutoImagem img1 = databaseService.inserirImagem(new ProdutoImagem("produto-001", "imagens/foto1.png", "url1", "foto1.png", true));
        ProdutoImagem img2 = databaseService.inserirImagem(new ProdutoImagem("produto-001", "imagens/foto2.png", "url2", "foto2.png", false));

        assertEquals(2, databaseService.listarImagensDoProduto("produto-001").size());

        // Apaga a foto 1 (que era a capa)
        boolean apagado = databaseService.excluirImagem(img1.getId());
        assertTrue(apagado);

        List<ProdutoImagem> restantes = databaseService.listarImagensDoProduto("produto-001");
        assertEquals(1, restantes.size());
        assertEquals(img2.getId(), restantes.get(0).getId());
        // A foto restante deve ter sido promovida a capa
        assertTrue(restantes.get(0).isEhPrincipal());
    }

    @Test
    @DisplayName("Deve excluir produto e todas as suas imagens em cascata no banco local")
    void deveExcluirProdutoEmCascata() {
        Produto produto = new Produto("produto-001", "Webcam 4K", "Foco automático", new BigDecimal("450.00"), "Vídeo");
        databaseService.salvarProduto(produto);
        databaseService.inserirImagem(new ProdutoImagem("produto-001", "imagens/foto1.png", "url1", "foto1.png", true));
        databaseService.inserirImagem(new ProdutoImagem("produto-001", "imagens/foto2.png", "url2", "foto2.png", false));

        assertEquals(1, databaseService.contarProdutos());
        assertEquals(2, databaseService.contarImagens());

        boolean excluido = databaseService.excluirProduto("produto-001");
        assertTrue(excluido);

        assertEquals(0, databaseService.contarProdutos());
        assertEquals(0, databaseService.contarImagens());
    }

    @Test
    @DisplayName("Deve gerar IDs sequenciais sem colisões")
    void deveGerarProximoNumeroId() {
        assertEquals(1, databaseService.gerarProximoNumeroId());

        databaseService.salvarProduto(new Produto("produto-001", "Item 1", "Desc", BigDecimal.TEN, "Cat"));
        databaseService.salvarProduto(new Produto("produto-002", "Item 2", "Desc", BigDecimal.TEN, "Cat"));

        assertEquals(3, databaseService.gerarProximoNumeroId());
    }
}
