package br.edu.fatecfranca.ecommerce.database;

import br.edu.fatecfranca.ecommerce.model.Produto;
import br.edu.fatecfranca.ecommerce.model.ProdutoImagem;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço de acesso ao Banco de Dados Local SQLite (ecommerce.db).
 * Garante a persistência de metadados dos produtos, referências às imagens no GCS,
 * marcação de imagem principal (capa) e datas.
 */
public class DatabaseService {

    public static final String NOME_BANCO_PADRAO = "ecommerce.db";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Pattern PADRAO_ID = Pattern.compile("produto-(\\d{3,})");

    private final String urlConexao;

    public DatabaseService() {
        this(NOME_BANCO_PADRAO);
    }

    public DatabaseService(String arquivoBanco) {
        this.urlConexao = "jdbc:sqlite:" + arquivoBanco;
        inicializarBanco();
    }

    private Connection obterConexao() throws SQLException {
        Connection conn = DriverManager.getConnection(urlConexao);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
    }

    private void inicializarBanco() {
        String sqlProdutos = """
                CREATE TABLE IF NOT EXISTS produtos (
                    id TEXT PRIMARY KEY,
                    nome TEXT NOT NULL,
                    descricao TEXT NOT NULL,
                    preco NUMERIC NOT NULL,
                    categoria TEXT NOT NULL,
                    data_cadastro TEXT NOT NULL
                );
                """;

        String sqlImagens = """
                CREATE TABLE IF NOT EXISTS produto_imagens (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    produto_id TEXT NOT NULL,
                    caminho_objeto TEXT NOT NULL,
                    url_publica TEXT NOT NULL,
                    nome_arquivo_original TEXT,
                    eh_principal INTEGER NOT NULL DEFAULT 0,
                    data_upload TEXT NOT NULL,
                    FOREIGN KEY (produto_id) REFERENCES produtos(id) ON DELETE CASCADE
                );
                """;

        String sqlIndice = """
                CREATE INDEX IF NOT EXISTS idx_produto_imagens_produto_id 
                ON produto_imagens(produto_id);
                """;

        try (Connection conn = obterConexao(); Statement stmt = conn.createStatement()) {
            stmt.execute(sqlProdutos);
            stmt.execute(sqlImagens);
            stmt.execute(sqlIndice);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao inicializar o banco de dados SQLite local (" + urlConexao + "): " + e.getMessage(), e);
        }
    }

    public synchronized void salvarProduto(Produto produto) {
        String sql = """
                INSERT INTO produtos (id, nome, descricao, preco, categoria, data_cadastro)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    nome = excluded.nome,
                    descricao = excluded.descricao,
                    preco = excluded.preco,
                    categoria = excluded.categoria;
                """;

        try (Connection conn = obterConexao(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, produto.getId());
            ps.setString(2, produto.getNome());
            ps.setString(3, produto.getDescricao());
            ps.setBigDecimal(4, produto.getPreco());
            ps.setString(5, produto.getCategoria());
            ps.setString(6, (produto.getDataCadastro() != null ? produto.getDataCadastro() : LocalDateTime.now()).format(FORMATTER));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar produto no SQLite: " + e.getMessage(), e);
        }
    }

    public Optional<Produto> buscarProdutoPorId(String id) {
        String sql = "SELECT id, nome, descricao, preco, categoria, data_cadastro FROM produtos WHERE id = ?";
        try (Connection conn = obterConexao(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Produto p = extrairProduto(rs);
                    p.setImagens(listarImagensDoProduto(conn, p.getId()));
                    return Optional.of(p);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar produto no SQLite: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<Produto> listarProdutos() {
        String sql = "SELECT id, nome, descricao, preco, categoria, data_cadastro FROM produtos ORDER BY id ASC";
        List<Produto> produtos = new ArrayList<>();
        try (Connection conn = obterConexao();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Produto p = extrairProduto(rs);
                p.setImagens(listarImagensDoProduto(conn, p.getId()));
                produtos.add(p);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar produtos no SQLite: " + e.getMessage(), e);
        }
        return produtos;
    }

    public boolean excluirProduto(String id) {
        String sql = "DELETE FROM produtos WHERE id = ?";
        try (Connection conn = obterConexao(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao excluir produto no SQLite: " + e.getMessage(), e);
        }
    }

    public synchronized ProdutoImagem inserirImagem(ProdutoImagem imagem) {
        // Se a imagem for marcada como principal, ou for a primeira do produto, desmarca as anteriores
        boolean definirComoCapa = imagem.isEhPrincipal();
        List<ProdutoImagem> existentes = listarImagensDoProduto(imagem.getProdutoId());
        if (existentes.isEmpty()) {
            definirComoCapa = true;
        }

        String sql = """
                INSERT INTO produto_imagens (produto_id, caminho_objeto, url_publica, nome_arquivo_original, eh_principal, data_upload)
                VALUES (?, ?, ?, ?, ?, ?);
                """;

        try (Connection conn = obterConexao()) {
            conn.setAutoCommit(false);
            try {
                if (definirComoCapa) {
                    try (PreparedStatement psZero = conn.prepareStatement(
                            "UPDATE produto_imagens SET eh_principal = 0 WHERE produto_id = ?")) {
                        psZero.setString(1, imagem.getProdutoId());
                        psZero.executeUpdate();
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, imagem.getProdutoId());
                    ps.setString(2, imagem.getCaminhoObjeto());
                    ps.setString(3, imagem.getUrlPublica());
                    ps.setString(4, imagem.getNomeArquivoOriginal());
                    ps.setInt(5, definirComoCapa ? 1 : 0);
                    ps.setString(6, (imagem.getDataUpload() != null ? imagem.getDataUpload() : LocalDateTime.now()).format(FORMATTER));
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) {
                            imagem.setId(keys.getLong(1));
                        }
                    }
                }

                conn.commit();
                imagem.setEhPrincipal(definirComoCapa);
                return imagem;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao inserir imagem no SQLite: " + e.getMessage(), e);
        }
    }

    public List<ProdutoImagem> listarImagensDoProduto(String produtoId) {
        try (Connection conn = obterConexao()) {
            return listarImagensDoProduto(conn, produtoId);
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar imagens do produto: " + e.getMessage(), e);
        }
    }

    private List<ProdutoImagem> listarImagensDoProduto(Connection conn, String produtoId) throws SQLException {
        String sql = """
                SELECT id, produto_id, caminho_objeto, url_publica, nome_arquivo_original, eh_principal, data_upload
                FROM produto_imagens
                WHERE produto_id = ?
                ORDER BY eh_principal DESC, id ASC;
                """;
        List<ProdutoImagem> imagens = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, produtoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    imagens.add(extrairImagem(rs));
                }
            }
        }
        return imagens;
    }

    public Optional<ProdutoImagem> buscarImagemPorId(Long imagemId) {
        String sql = """
                SELECT id, produto_id, caminho_objeto, url_publica, nome_arquivo_original, eh_principal, data_upload
                FROM produto_imagens
                WHERE id = ?;
                """;
        try (Connection conn = obterConexao(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, imagemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(extrairImagem(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar imagem por ID: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public synchronized boolean definirImagemPrincipal(String produtoId, Long imagemId) {
        try (Connection conn = obterConexao()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement psReset = conn.prepareStatement(
                        "UPDATE produto_imagens SET eh_principal = 0 WHERE produto_id = ?")) {
                    psReset.setString(1, produtoId);
                    psReset.executeUpdate();
                }

                int alterados;
                try (PreparedStatement psSet = conn.prepareStatement(
                        "UPDATE produto_imagens SET eh_principal = 1 WHERE produto_id = ? AND id = ?")) {
                    psSet.setString(1, produtoId);
                    psSet.setLong(2, imagemId);
                    alterados = psSet.executeUpdate();
                }

                conn.commit();
                return alterados > 0;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao definir imagem principal: " + e.getMessage(), e);
        }
    }

    public synchronized boolean excluirImagem(Long imagemId) {
        Optional<ProdutoImagem> imgOpt = buscarImagemPorId(imagemId);
        if (imgOpt.isEmpty()) {
            return false;
        }

        ProdutoImagem img = imgOpt.get();
        String produtoId = img.getProdutoId();
        boolean eraPrincipal = img.isEhPrincipal();

        String sqlDelete = "DELETE FROM produto_imagens WHERE id = ?";
        try (Connection conn = obterConexao()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement psDel = conn.prepareStatement(sqlDelete)) {
                    psDel.setLong(1, imagemId);
                    psDel.executeUpdate();
                }

                // Se era principal e ainda restam imagens, promove a primeira restante a principal
                if (eraPrincipal) {
                    try (PreparedStatement psPromo = conn.prepareStatement(
                            "UPDATE produto_imagens SET eh_principal = 1 WHERE id = (SELECT id FROM produto_imagens WHERE produto_id = ? ORDER BY id ASC LIMIT 1)")) {
                        psPromo.setString(1, produtoId);
                        psPromo.executeUpdate();
                    }
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao excluir imagem no SQLite: " + e.getMessage(), e);
        }
    }

    public int gerarProximoNumeroId() {
        String sql = "SELECT id FROM produtos";
        int maior = 0;
        try (Connection conn = obterConexao();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString("id");
                Matcher m = PADRAO_ID.matcher(id);
                if (m.find()) {
                    try {
                        int num = Integer.parseInt(m.group(1));
                        if (num > maior) {
                            maior = num;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao calcular próximo ID sequencial: " + e.getMessage(), e);
        }
        return maior + 1;
    }

    public int contarProdutos() {
        String sql = "SELECT COUNT(*) FROM produtos";
        try (Connection conn = obterConexao();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao contar produtos: " + e.getMessage(), e);
        }
        return 0;
    }

    public int contarImagens() {
        String sql = "SELECT COUNT(*) FROM produto_imagens";
        try (Connection conn = obterConexao();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao contar imagens: " + e.getMessage(), e);
        }
        return 0;
    }

    private Produto extrairProduto(ResultSet rs) throws SQLException {
        Produto p = new Produto();
        p.setId(rs.getString("id"));
        p.setNome(rs.getString("nome"));
        p.setDescricao(rs.getString("descricao"));
        BigDecimal preco = rs.getBigDecimal("preco");
        p.setPreco(preco != null ? preco.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO);
        p.setCategoria(rs.getString("categoria"));
        String dataStr = rs.getString("data_cadastro");
        if (dataStr != null) {
            p.setDataCadastro(LocalDateTime.parse(dataStr, FORMATTER));
        }
        return p;
    }

    private ProdutoImagem extrairImagem(ResultSet rs) throws SQLException {
        ProdutoImagem img = new ProdutoImagem();
        img.setId(rs.getLong("id"));
        img.setProdutoId(rs.getString("produto_id"));
        img.setCaminhoObjeto(rs.getString("caminho_objeto"));
        img.setUrlPublica(rs.getString("url_publica"));
        img.setNomeArquivoOriginal(rs.getString("nome_arquivo_original"));
        img.setEhPrincipal(rs.getInt("eh_principal") == 1);
        String dataStr = rs.getString("data_upload");
        if (dataStr != null) {
            img.setDataUpload(LocalDateTime.parse(dataStr, FORMATTER));
        }
        return img;
    }
}
