package br.edu.fatecfranca.ecommerce.service;

import com.google.api.gax.paging.Page;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Serviço responsável exclusivamente pelas operações no Google Cloud Storage (GCS).
 * Encapsula autenticação ADC (Application Default Credentials), upload via stream,
 * listagem por prefixo, leitura/download, verificação de existência e exclusão.
 */
public class GcsStorageService {

    public static final String PROJECT_ID = "serjava-demo";
    public static final String BUCKET_NAME = "fatec-2026-grupo-i";
    public static final String PUBLIC_BASE_URL = "https://storage.googleapis.com/" + BUCKET_NAME + "/";

    private final Storage storage;

    /**
     * Inicializa o cliente do Google Cloud Storage utilizando o mecanismo padrão
     * de credenciais (variável de ambiente GOOGLE_APPLICATION_CREDENTIALS).
     */
    public GcsStorageService() {
        this.storage = inicializarStorage();
    }

    private Storage inicializarStorage() {
        String credPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credPath == null || credPath.isBlank()) {
            throw new IllegalStateException(
                    "A variável de ambiente GOOGLE_APPLICATION_CREDENTIALS não está definida.\n" +
                    "No PowerShell, configure-a apontando para o arquivo sa-grupo-i-key.json:\n" +
                    "$env:GOOGLE_APPLICATION_CREDENTIALS=\"$(Get-Location)\\sa-grupo-i-key.json\""
            );
        }

        Path path = Path.of(credPath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalStateException(
                    "O arquivo de credenciais configurado em GOOGLE_APPLICATION_CREDENTIALS não foi encontrado: "
                    + path.toAbsolutePath()
            );
        }

        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(Files.newInputStream(path));
            return StorageOptions.newBuilder()
                    .setProjectId(PROJECT_ID)
                    .setCredentials(credentials)
                    .build()
                    .getService();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Falha ao ler ou processar o arquivo de credenciais da Service Account. " +
                    "Verifique se o JSON é válido.", e
            );
        }
    }

    /**
     * Envia um conteúdo de texto (como JSON em UTF-8) para o bucket via stream.
     *
     * @param objectName  Caminho do objeto dentro do bucket (ex: "produtos/produto-001.json")
     * @param conteudo    Conteúdo em texto UTF-8
     * @param contentType Tipo MIME (ex: "application/json; charset=utf-8")
     * @return Blob criado no GCS
     */
    public Blob uploadTextoUtf8(String objectName, String conteudo, String contentType) {
        byte[] bytes = conteudo.getBytes(StandardCharsets.UTF_8);
        BlobId blobId = BlobId.of(BUCKET_NAME, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(contentType)
                .build();

        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            return storage.createFrom(blobInfo, inputStream);
        } catch (StorageException e) {
            throw tratarStorageException("enviar objeto JSON '" + objectName + "'", e);
        } catch (IOException e) {
            throw new RuntimeException("Erro de I/O ao enviar stream de texto para o GCS: " + e.getMessage(), e);
        }
    }

    /**
     * Envia um arquivo local (ex: imagem PNG/JPG) para o bucket via stream,
     * conforme documentação oficial (storage_stream_file_upload).
     *
     * @param objectName   Caminho do objeto no bucket (ex: "imagens/produto-001.png")
     * @param arquivoLocal Caminho local do arquivo a ser enviado
     * @param contentType  Tipo MIME (ex: "image/png" ou "image/jpeg")
     * @return Blob criado no GCS
     */
    public Blob uploadArquivo(String objectName, Path arquivoLocal, String contentType) {
        if (!Files.exists(arquivoLocal) || !Files.isRegularFile(arquivoLocal)) {
            throw new IllegalArgumentException("O arquivo local informado não existe: " + arquivoLocal.toAbsolutePath());
        }

        BlobId blobId = BlobId.of(BUCKET_NAME, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(contentType)
                .build();

        try (InputStream inputStream = Files.newInputStream(arquivoLocal)) {
            return storage.createFrom(blobInfo, inputStream);
        } catch (StorageException e) {
            throw tratarStorageException("enviar arquivo '" + objectName + "'", e);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler arquivo local para envio ao GCS: " + e.getMessage(), e);
        }
    }

    /**
     * Verifica de forma real no GCS se um objeto existe no bucket.
     *
     * @param objectName Caminho do objeto dentro do bucket
     * @return true se o objeto existir no bucket, false caso contrário
     */
    public boolean objetoExiste(String objectName) {
        try {
            Blob blob = storage.get(BlobId.of(BUCKET_NAME, objectName));
            return blob != null && blob.exists();
        } catch (StorageException e) {
            throw tratarStorageException("verificar existência do objeto '" + objectName + "'", e);
        }
    }

    /**
     * Lista os caminhos (nomes) de todos os objetos no bucket que começam com determinado prefixo.
     *
     * @param prefixo Prefixo de diretório lógico (ex: "produtos/" ou "imagens/")
     * @return Lista de nomes de objetos encontrados
     */
    public List<String> listarObjetosPorPrefixo(String prefixo) {
        List<String> nomesObjetos = new ArrayList<>();
        try {
            Page<Blob> blobs = storage.list(
                    BUCKET_NAME,
                    Storage.BlobListOption.prefix(prefixo)
            );
            for (Blob blob : blobs.iterateAll()) {
                // Ignora marcadores de diretório vazios caso existam no bucket
                if (!blob.getName().endsWith("/")) {
                    nomesObjetos.add(blob.getName());
                }
            }
            return nomesObjetos;
        } catch (StorageException e) {
            throw tratarStorageException("listar objetos com prefixo '" + prefixo + "'", e);
        }
    }

    /**
     * Lê o conteúdo de um objeto do bucket como texto UTF-8.
     *
     * @param objectName Caminho do objeto dentro do bucket
     * @return Optional contendo o texto caso o objeto exista, ou vazio se não existir
     */
    public Optional<String> lerObjetoComoTexto(String objectName) {
        try {
            Blob blob = storage.get(BlobId.of(BUCKET_NAME, objectName));
            if (blob == null || !blob.exists()) {
                return Optional.empty();
            }
            byte[] conteudo = blob.getContent();
            return Optional.of(new String(conteudo, StandardCharsets.UTF_8));
        } catch (StorageException e) {
            throw tratarStorageException("ler objeto '" + objectName + "'", e);
        }
    }

    /**
     * Realiza o download de um objeto armazenado no GCS e salva em um arquivo local.
     *
     * @param objectName     Caminho do objeto dentro do bucket
     * @param destinoArquivo Caminho local onde o arquivo será salvo
     * @return true se o download foi concluído, false se o objeto não existir no bucket
     */
    public boolean baixarObjetoParaArquivo(String objectName, Path destinoArquivo) {
        try {
            Blob blob = storage.get(BlobId.of(BUCKET_NAME, objectName));
            if (blob == null || !blob.exists()) {
                return false;
            }
            if (destinoArquivo.getParent() != null) {
                Files.createDirectories(destinoArquivo.getParent());
            }
            blob.downloadTo(destinoArquivo);
            return true;
        } catch (StorageException e) {
            throw tratarStorageException("baixar objeto '" + objectName + "'", e);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao criar diretório ou salvar arquivo local: " + e.getMessage(), e);
        }
    }

    /**
     * Exclui um objeto do bucket do Google Cloud Storage.
     *
     * @param objectName Caminho do objeto dentro do bucket
     * @return true se o objeto existia e foi removido, false se não foi encontrado
     */
    public boolean excluirObjeto(String objectName) {
        try {
            return storage.delete(BlobId.of(BUCKET_NAME, objectName));
        } catch (StorageException e) {
            throw tratarStorageException("excluir objeto '" + objectName + "'", e);
        }
    }

    /**
     * Gera a URL pública de leitura para um objeto do bucket, codificando cada segmento
     * do caminho para tratar caracteres especiais com segurança (RFC 3986 / URL encoding).
     * Atenção: montar a URL não garante que o objeto exista; verifique antes via objetoExiste() se necessário.
     *
     * @param objectName Caminho do objeto no bucket (ex: "produtos/produto-001.json")
     * @return URL pública formatada (ex: "https://storage.googleapis.com/fatec-2026-grupo-i/produtos/produto-001.json")
     */
    public String gerarUrlPublica(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("O caminho do objeto não pode ser vazio.");
        }
        String caminhoCodificado = Arrays.stream(objectName.split("/"))
                .filter(segmento -> !segmento.isEmpty())
                .map(segmento -> URLEncoder.encode(segmento, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));

        return PUBLIC_BASE_URL + caminhoCodificado;
    }

    /**
     * Traduz exceções HTTP do Google Cloud Storage em mensagens claras para diagnóstico acadêmico.
     */
    private RuntimeException tratarStorageException(String operacao, StorageException e) {
        int codigo = e.getCode();
        String explicacao = switch (codigo) {
            case 401 -> "Falha de autenticação (HTTP 401). Verifique se a chave JSON da Service Account está ativa e íntegra.";
            case 403 -> "Permissão negada (HTTP 403). Verifique se a Service Account 'sa-grupo-i@serjava-demo.iam.gserviceaccount.com' possui o papel 'roles/storage.objectAdmin' no bucket '" + BUCKET_NAME + "'.";
            case 404 -> "Recurso ou bucket não encontrado (HTTP 404). Confirme o nome do bucket '" + BUCKET_NAME + "' e o caminho do objeto.";
            default -> "Erro de comunicação com o Google Cloud Storage (HTTP " + codigo + "): " + e.getMessage();
        };
        return new RuntimeException("Erro ao " + operacao + " -> " + explicacao, e);
    }
}
