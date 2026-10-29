# FatecShop Cloud — E-commerce com Google Cloud Storage (GCS)

Projeto acadêmico desenvolvido em **Java 21** integrado ao **Google Cloud Storage (GCS)** com **Interface Web de E-commerce (HTML5, CSS3 e JavaScript)** e API REST.

---

## Integrantes (Grupo I — Fatec Franca / DSM)
- **Hugo de Castro Rodrigues**
- **Pablo Miguel Sousa Nóbrega**
- **Arthur Rodrigues Ferreira**

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
├── pom.xml
├── mvnw / mvnw.cmd
└── src/
    └── main/
        ├── java/br/edu/fatecfranca/ecommerce/
        │   ├── Main.java                       # Inicializa Servidor Web (porta 8080) e Menu Console
        │   ├── model/
        │   │   └── Produto.java                # Entidade de domínio (BigDecimal, metadados GCS)
        │   ├── service/
        │   │   ├── GcsStorageService.java      # Operações oficiais do SDK google-cloud-storage
        │   │   └── ProdutoService.java         # Regras de catálogo, geração de IDs e serialização JSON
        │   ├── util/
        │   │   └── ValidadorEntrada.java       # Validações de dados de entrada
        │   └── web/
        │       └── ServidorWebEcommerce.java   # API REST + Servidor HTTP para o Frontend Web
        └── resources/web/
            ├── index.html                      # Interface Gráfica da Loja Virtual (FatecShop Cloud)
            ├── styles.css                      # Estilização responsiva do E-commerce
            └── app.js                          # Integração Frontend com a API Java / GCS
```

---

## Funcionalidades Implementadas

1. **Cadastrar produto:** Registra nome, descrição, preço (`BigDecimal`) e categoria.
2. **Armazenar dados no GCS:** Serializa o produto em JSON (`UTF-8`) via Jackson e envia para `produtos/produto-XXX.json` no bucket.
3. **Upload de imagens:** Envia a imagem do produto via stream (`storage.createFrom`) para `imagens/produto-XXX.<ext>` e vincula a URL pública ao JSON do produto.
4. **Listar produtos:** Consulta os objetos `produtos/*.json` armazenados no bucket e exibe na vitrine Web e no console.
5. **Consultar produto:** Localiza um produto específico e exibe seus metadados e o JSON bruto lido diretamente do GCS.
6. **Demonstrar download ou leitura:** Permite visualizar o conteúdo UTF-8 de qualquer objeto do bucket ou baixá-lo localmente para a pasta `downloads/`.
7. **Excluir produto:** Remove o arquivo JSON e a imagem correspondente do bucket mediante confirmação prévia.

---

## Configuração e Execução

### 1. Configurar a credencial da Service Account
Posicione o arquivo `sa-grupo-i-key.json` (fornecido pelo professor) na raiz do projeto e configure a variável de ambiente `GOOGLE_APPLICATION_CREDENTIALS` no PowerShell:

```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS="$(Get-Location)\sa-grupo-i-key.json"
```

> **Segurança:** O arquivo `sa-grupo-i-key.json` está listado no `.gitignore` e nunca deve ser enviado ao repositório Git.

### 2. Compilar e executar a aplicação

```powershell
mvn clean compile exec:java
```

*(Ou utilizando o Maven Wrapper incluído no projeto: `.\mvnw.cmd clean compile exec:java`).*

### 3. Acessar a Interface Web
Ao iniciar, a aplicação disponibiliza automaticamente o E-commerce em:
- **http://localhost:8080**
