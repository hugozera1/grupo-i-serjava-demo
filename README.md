# FatecShop Cloud — E-commerce com Banco de Dados SQLite e Google Cloud Storage (GCS)

Projeto acadêmico desenvolvido em **Java 21** com **Banco de Dados Local SQLite (`ecommerce.db`)**, integrado ao **Google Cloud Storage (GCS)**, **Interface Web de E-commerce (HTML5, CSS3 e JavaScript)** e API REST.

---

## Integrantes (Grupo I — Fatec Franca / DSM)
- **Hugo de Castro Rodrigues**
- **Pablo Miguel Sousa Nóbrega**
- **Arthur Rodrigues Ferreira**

---

## Conformidade com os Requisitos do Projeto

1. **Requisito 1 & Requisito Técnico 1 — Banco de Dados Local + Apenas Imagens no Bucket:**
   - Os dados dos registros (entidade produto, referências às imagens no GCS, flag de imagem principal/capa e datas) ficam armazenados em um banco de dados local **SQLite embutido (`ecommerce.db`)**.
   - O bucket do Google Cloud Storage armazena **exclusivamente os arquivos binários de imagem** (sob `imagens/`).
2. **Requisito 2 — Bucket e Chave Exclusivos do Grupo I:**
   - Bucket: `fatec-2026-grupo-i`
   - Projeto: `serjava-demo`
   - Credencial: `sa-grupo-i-key.json` autenticada via SDK oficial (`GOOGLE_APPLICATION_CREDENTIALS`).
3. **Requisito 3 — Upload, Listagem e Download via Aplicação Java:**
   - Upload de imagens via stream (`storage.createFrom` / `uploadBytes`).
   - Listagem de objetos e download direto pela aplicação Java tanto na Web quanto no menu Console.
4. **Requisito 4 — URLs Públicas Abertas Diretamente no Navegador:**
   - URLs no formato `https://storage.googleapis.com/fatec-2026-grupo-i/imagens/...` abrem diretamente no navegador sem autenticação.
5. **Requisito Técnico 5 — Carga de Demonstração (10 Produtos x 3 Imagens cada = 30 fotos reais):**
   - Rotina automatizada que cadastra 10 produtos no SQLite e gera/envia 3 imagens reais para cada produto no bucket GCS (total de 30 fotos), já com a imagem principal devidamente marcada.
6. **Requisito Técnico 6 — Múltiplas Imagens por Produto, Capa e Exclusão Individual:**
   - Suporta $N$ imagens por produto.
   - Vitrine exibe a miniatura da imagem principal (capa).
   - Tela de detalhes exibe a capa em destaque + galeria de fotos com opções: "Definir como Capa", "Baixar" e "Apagar Imagem" (remove do GCS e do SQLite).

---

## Infraestrutura Google Cloud
- **Provedor:** Google Cloud Storage (GCS)
- **Projeto:** `serjava-demo`
- **Bucket:** `fatec-2026-grupo-i` (`us-central1` • classe `STANDARD`)
- **Service Account:** `sa-grupo-i@serjava-demo.iam.gserviceaccount.com` (`roles/storage.objectAdmin`)
- **URL Pública do Bucket:** [https://storage.googleapis.com/fatec-2026-grupo-i/](https://storage.googleapis.com/fatec-2026-grupo-i/)

---

## Arquitetura do Projeto

```text
grupo-i-serjava-demo/
├── .gitignore
├── pom.xml                                     # Dependências: GCS SDK, SQLite JDBC, Jackson
├── mvnw / mvnw.cmd                             # Maven Wrapper
└── src/
    └── main/
        ├── java/br/edu/fatecfranca/ecommerce/
        │   ├── Main.java                       # Servidor Web (porta 8080) e Menu Console interativo
        │   ├── database/
        │   │   └── DatabaseService.java        # Acesso ao SQLite local (ecommerce.db): produtos e produto_imagens
        │   ├── model/
        │   │   ├── Produto.java                # Entidade de domínio (preço BigDecimal, capa e N fotos)
        │   │   └── ProdutoImagem.java          # Metadados de cada foto (caminho GCS, URL pública, flag capa)
        │   ├── service/
        │   │   ├── GcsStorageService.java      # Operações oficiais do SDK google-cloud-storage (upload stream, get, list, delete)
        │   │   └── ProdutoService.java         # Regras de negócio, geração de fotos demo e orquestração SQLite + GCS
        │   ├── util/
        │   │   └── ValidadorEntrada.java       # Validações de entradas do usuário
        │   └── web/
        │       └── ServidorWebEcommerce.java   # API REST e Servidor HTTP embutido para a Interface Web
        └── resources/web/
            ├── index.html                      # Vitrine Web, Modal de Detalhes, Galeria e Carga Demo
            ├── styles.css                      # Estilização responsiva do e-commerce
            └── app.js                          # Integração Frontend (Fetch API) com backend Java
```

---

## Configuração e Execução

### 1. Credencial da Service Account (`sa-grupo-i-key.json`)
Posicione o arquivo `sa-grupo-i-key.json` na raiz do projeto (ou configure a variável de ambiente):

```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS="$(Get-Location)\sa-grupo-i-key.json"
```

> **Segurança:** O arquivo `sa-grupo-i-key.json` e os bancos `*.db` estão listados no `.gitignore` e nunca são versionados no Git.

### 2. Compilar e Executar a Aplicação

Usando o Maven Wrapper incluído no projeto:

```powershell
.\mvnw.cmd clean compile exec:java
```

*(Ou usando Maven instalado: `mvn clean compile exec:java`).*

### 3. Acessar a Loja Virtual (Interface Web)
Ao iniciar, o navegador pode ser aberto automaticamente, ou você pode acessar:
- **http://localhost:8080**

### 4. Demonstrar a Carga de 10 Produtos com 3 Imagens cada (Req. 5)
- **Pela Interface Web:** Clique no botão superior **"⚡ Carga Demo (10 produtos x 3 fotos)"**.
- **Pelo Console:** Escolha a opção **6. Gerar Carga de Demonstração**.
- A aplicação gerará automaticamente 10 produtos no banco local SQLite e fará o upload de 30 imagens reais para o bucket `fatec-2026-grupo-i` no GCS, definindo a imagem principal de cada um!
