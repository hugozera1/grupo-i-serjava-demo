// Estado da aplicação no Frontend
let listaProdutosCache = [];
let idProdutoParaUploadImagem = null;

// Elementos do DOM
const gridProdutos = document.getElementById('gridProdutos');
const listaObjetosProdutos = document.getElementById('listaObjetosProdutos');
const listaObjetosImagens = document.getElementById('listaObjetosImagens');
const statTotalProdutos = document.getElementById('statTotalProdutos');
const statTotalImagens = document.getElementById('statTotalImagens');
const inputBusca = document.getElementById('inputBusca');
const toastNotificacao = document.getElementById('toastNotificacao');

// Modal de Cadastro
const modalCadastro = document.getElementById('modalCadastro');
const formCadastroProduto = document.getElementById('formCadastroProduto');
const btnSalvarProduto = document.getElementById('btnSalvarProduto');

// Modal de Detalhes / Leitura JSON
const modalDetalhes = document.getElementById('modalDetalhes');
const tituloModalDetalhes = document.getElementById('tituloModalDetalhes');
const corpoModalDetalhes = document.getElementById('corpoModalDetalhes');

// Upload rápido de imagem para produto existente
const inputUploadImagemRapido = document.getElementById('inputUploadImagemRapido');

document.addEventListener('DOMContentLoaded', () => {
    carregarTudoDoBucket();

    document.getElementById('btnRecarregar').addEventListener('click', () => {
        mostrarToast('Consultando objetos atualizados no Google Cloud Storage...');
        carregarTudoDoBucket();
    });

    document.getElementById('btnAbrirModalCadastro').addEventListener('click', () => {
        formCadastroProduto.reset();
        modalCadastro.classList.remove('hidden');
    });

    document.getElementById('btnFecharModalCadastro').addEventListener('click', () => {
        modalCadastro.classList.add('hidden');
    });

    document.getElementById('btnCancelarCadastro').addEventListener('click', () => {
        modalCadastro.classList.add('hidden');
    });

    document.getElementById('btnFecharModalDetalhes').addEventListener('click', () => {
        modalDetalhes.classList.add('hidden');
    });

    inputBusca.addEventListener('input', () => {
        renderizarVitrine(listaProdutosCache);
    });

    formCadastroProduto.addEventListener('submit', salvarNovoProduto);

    inputUploadImagemRapido.addEventListener('change', async (e) => {
        const arquivo = e.target.files[0];
        if (!arquivo || !idProdutoParaUploadImagem) return;
        try {
            mostrarToast(`Enviando imagem '${arquivo.name}' para o bucket para o ${idProdutoParaUploadImagem}...`);
            await enviarImagemProduto(idProdutoParaUploadImagem, arquivo);
            mostrarToast(`Imagem enviada com sucesso para o Google Cloud Storage!`);
            await carregarTudoDoBucket();
        } catch (err) {
            mostrarToast(err.message || 'Erro ao enviar imagem.', true);
        } finally {
            inputUploadImagemRapido.value = '';
            idProdutoParaUploadImagem = null;
        }
    });
});

async function carregarTudoDoBucket() {
    await Promise.all([
        carregarProdutos(),
        carregarObjetosDoBucket()
    ]);
}

async function carregarProdutos() {
    try {
        const resp = await fetch('/api/produtos');
        const dados = await resp.json();
        if (!resp.ok) throw new Error(dados.mensagem || 'Erro ao listar produtos do GCS');

        listaProdutosCache = dados;
        statTotalProdutos.textContent = `${dados.length} produto(s)`;
        renderizarVitrine(dados);
    } catch (err) {
        gridProdutos.innerHTML = `<div class="empty-state">Erro ao comunicar com o backend Java / GCS: ${err.message}</div>`;
    }
}

async function carregarObjetosDoBucket() {
    try {
        const resp = await fetch('/api/objetos');
        const dados = await resp.json();
        if (!resp.ok) throw new Error(dados.mensagem || 'Erro ao listar objetos');

        const objsProd = dados.objetosProdutos || [];
        const objsImg = dados.objetosImagens || [];
        statTotalImagens.textContent = `${objsImg.length} imagem(ns)`;

        if (objsProd.length === 0) {
            listaObjetosProdutos.innerHTML = `<li class="object-item">Nenhum arquivo JSON em <code>produtos/</code></li>`;
        } else {
            listaObjetosProdutos.innerHTML = objsProd.map(caminho => {
                const urlPublica = `${dados.urlPublicaBase}${caminho}`;
                return `
                    <li class="object-item">
                        <code>${caminho}</code>
                        <div class="object-actions">
                            <button class="btn btn-secondary btn-sm" onclick="inspecionarObjetoGcs('${caminho}')">Ler JSON</button>
                            <a class="btn btn-secondary btn-sm" href="/api/objetos/download?caminho=${encodeURIComponent(caminho)}">Baixar</a>
                            <a class="btn btn-secondary btn-sm" href="${urlPublica}" target="_blank" rel="noopener">URL ↗</a>
                        </div>
                    </li>
                `;
            }).join('');
        }

        if (objsImg.length === 0) {
            listaObjetosImagens.innerHTML = `<li class="object-item">Nenhuma imagem em <code>imagens/</code></li>`;
        } else {
            listaObjetosImagens.innerHTML = objsImg.map(caminho => {
                const urlPublica = `${dados.urlPublicaBase}${caminho}`;
                return `
                    <li class="object-item">
                        <code>${caminho}</code>
                        <div class="object-actions">
                            <a class="btn btn-secondary btn-sm" href="/api/objetos/download?caminho=${encodeURIComponent(caminho)}">Baixar</a>
                            <a class="btn btn-secondary btn-sm" href="${urlPublica}" target="_blank" rel="noopener">Abrir Imagem ↗</a>
                        </div>
                    </li>
                `;
            }).join('');
        }
    } catch (err) {
        listaObjetosProdutos.innerHTML = `<li>Erro: ${err.message}</li>`;
    }
}

function renderizarVitrine(produtos) {
    const termo = (inputBusca.value || '').trim().toLowerCase();
    const filtrados = produtos.filter(p => {
        if (!termo) return true;
        return (p.nome && p.nome.toLowerCase().includes(termo)) ||
               (p.id && p.id.toLowerCase().includes(termo)) ||
               (p.categoria && p.categoria.toLowerCase().includes(termo)) ||
               (p.descricao && p.descricao.toLowerCase().includes(termo));
    });

    if (filtrados.length === 0) {
        gridProdutos.innerHTML = `
            <div class="empty-state">
                <h3>Nenhum produto encontrado no bucket</h3>
                <p>Clique em <strong>"+ Cadastrar Produto"</strong> no topo da página para enviar um novo produto ao Google Cloud Storage.</p>
            </div>
        `;
        return;
    }

    gridProdutos.innerHTML = filtrados.map(p => {
        const precoFormatado = Number(p.preco || 0).toLocaleString('pt-BR', {
            style: 'currency',
            currency: 'BRL'
        });

        // Cache-buster leve na exibição para refletir trocas de imagem imediatamente
        const htmlImagem = p.urlPublicaImagem
            ? `<img src="${p.urlPublicaImagem}?t=${Date.now()}" alt="${escaparHtml(p.nome)}">`
            : `<div class="no-image-placeholder">
                   <p>Sem imagem associada no bucket</p>
                   <button class="btn btn-secondary btn-sm" style="margin-top:8px" onclick="dispararUploadImagem('${p.id}')">
                       📤 Enviar Imagem para o GCS
                   </button>
               </div>`;

        return `
            <article class="product-card">
                <div class="product-image-box">
                    <span class="badge-id">${escaparHtml(p.id)}</span>
                    <span class="badge-category">${escaparHtml(p.categoria)}</span>
                    ${htmlImagem}
                </div>
                <div class="product-body">
                    <h3 class="product-title">${escaparHtml(p.nome)}</h3>
                    <div class="product-price">${precoFormatado}</div>
                    <p class="product-desc">${escaparHtml(p.descricao)}</p>

                    <div class="gcs-meta">
                        <div><strong>Objeto JSON:</strong> <a href="${p.urlPublicaJson}" target="_blank" rel="noopener">${p.caminhoObjetoJson} ↗</a></div>
                        <div><strong>Objeto Imagem:</strong> ${
                            p.urlPublicaImagem
                                ? `<a href="${p.urlPublicaImagem}" target="_blank" rel="noopener">${p.caminhoObjetoImagem} ↗</a>`
                                : `<span>(não enviada)</span>`
                        }</div>
                    </div>
                </div>
                <div class="product-actions">
                    <button class="btn btn-secondary btn-sm" onclick="consultarProdutoDetalhes('${p.id}')">
                        🔍 Consultar / JSON
                    </button>
                    <button class="btn btn-secondary btn-sm" onclick="dispararUploadImagem('${p.id}')">
                        🖼️ ${p.urlPublicaImagem ? 'Trocar Foto' : 'Enviar Foto'}
                    </button>
                    <a class="btn btn-secondary btn-sm" href="/api/objetos/download?caminho=${encodeURIComponent(p.caminhoObjetoJson)}">
                        ⬇️ Baixar JSON
                    </a>
                    <button class="btn btn-danger btn-sm" onclick="confirmarExclusaoProduto('${p.id}', '${escaparHtml(p.nome)}')">
                        🗑️ Excluir
                    </button>
                </div>
            </article>
        `;
    }).join('');
}

async function salvarNovoProduto(e) {
    e.preventDefault();
    const nome = document.getElementById('campoNome').value.trim();
    const categoria = document.getElementById('campoCategoria').value.trim();
    const preco = document.getElementById('campoPreco').value.trim();
    const descricao = document.getElementById('campoDescricao').value.trim();
    const arquivoImagem = document.getElementById('campoImagem').files[0];

    btnSalvarProduto.disabled = true;
    btnSalvarProduto.textContent = 'Enviando para o GCS...';

    try {
        // 1. Cria o produto e envia o arquivo JSON para "produtos/produto-XXX.json"
        const resp = await fetch('/api/produtos', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ nome, categoria, preco, descricao })
        });
        const produtoCriado = await resp.json();
        if (!resp.ok) throw new Error(produtoCriado.mensagem || 'Erro ao cadastrar produto.');

        // 2. Se o usuário selecionou uma imagem, envia para "imagens/produto-XXX.<ext>"
        if (arquivoImagem) {
            await enviarImagemProduto(produtoCriado.id, arquivoImagem);
        }

        modalCadastro.classList.add('hidden');
        mostrarToast(`Produto '${produtoCriado.id}' (${produtoCriado.nome}) salvo com sucesso no bucket fatec-2026-grupo-i!`);
        await carregarTudoDoBucket();
    } catch (err) {
        mostrarToast(err.message, true);
    } finally {
        btnSalvarProduto.disabled = false;
        btnSalvarProduto.textContent = 'Salvar no Google Cloud Storage';
    }
}

async function enviarImagemProduto(idProduto, arquivo) {
    const resp = await fetch(`/api/produtos/${encodeURIComponent(idProduto)}/imagem?nomeArquivo=${encodeURIComponent(arquivo.name)}`, {
        method: 'POST',
        headers: { 'Content-Type': arquivo.type || 'application/octet-stream' },
        body: arquivo
    });
    const dados = await resp.json();
    if (!resp.ok) throw new Error(dados.mensagem || 'Falha no upload da imagem para o GCS.');
    return dados;
}

function dispararUploadImagem(idProduto) {
    idProdutoParaUploadImagem = idProduto;
    inputUploadImagemRapido.click();
}

async function consultarProdutoDetalhes(idProduto) {
    try {
        const resp = await fetch(`/api/produtos/${encodeURIComponent(idProduto)}`);
        const p = await resp.json();
        if (!resp.ok) throw new Error(p.mensagem || 'Produto não encontrado.');

        const jsonResp = await fetch(`/api/objetos/conteudo?caminho=${encodeURIComponent(p.caminhoObjetoJson)}`);
        const jsonBruto = await jsonResp.json();

        tituloModalDetalhes.textContent = `Consulta de Produto no GCS: ${p.id}`;
        corpoModalDetalhes.innerHTML = `
            <div>
                <p><strong>Nome:</strong> ${escaparHtml(p.nome)}</p>
                <p><strong>Categoria:</strong> ${escaparHtml(p.categoria)}</p>
                <p><strong>Preço (BigDecimal):</strong> R$ ${p.preco}</p>
                <p><strong>Descrição:</strong> ${escaparHtml(p.descricao)}</p>
                <p><strong>Caminho do Objeto JSON:</strong> <code>${p.caminhoObjetoJson}</code></p>
                <p><strong>URL Pública JSON:</strong> <a href="${p.urlPublicaJson}" target="_blank" rel="noopener">${p.urlPublicaJson} ↗</a></p>
                <p><strong>Caminho da Imagem:</strong> <code>${p.caminhoObjetoImagem || '(sem imagem)'}</code></p>
                ${p.urlPublicaImagem ? `<p><strong>URL Pública Imagem:</strong> <a href="${p.urlPublicaImagem}" target="_blank" rel="noopener">${p.urlPublicaImagem} ↗</a></p>` : ''}
            </div>
            <div>
                <h4 style="margin-bottom:6px">Conteúdo bruto lido diretamente do Google Cloud Storage (UTF-8):</h4>
                <pre class="json-viewer">${escaparHtml(jsonBruto.conteudo || '')}</pre>
            </div>
            <div class="modal-footer">
                <a class="btn btn-primary" href="/api/objetos/download?caminho=${encodeURIComponent(p.caminhoObjetoJson)}">
                    ⬇️ Baixar JSON (salva em downloads/)
                </a>
            </div>
        `;
        modalDetalhes.classList.remove('hidden');
    } catch (err) {
        mostrarToast(err.message, true);
    }
}

async function inspecionarObjetoGcs(caminhoObjeto) {
    try {
        const resp = await fetch(`/api/objetos/conteudo?caminho=${encodeURIComponent(caminhoObjeto)}`);
        const dados = await resp.json();
        if (!resp.ok) throw new Error(dados.mensagem || 'Erro ao ler objeto.');

        tituloModalDetalhes.textContent = `Leitura Direta de Objeto: ${dados.caminho}`;
        corpoModalDetalhes.innerHTML = `
            <p><strong>URL Pública no GCS:</strong> <a href="${dados.urlPublica}" target="_blank" rel="noopener">${dados.urlPublica} ↗</a></p>
            <pre class="json-viewer">${escaparHtml(dados.conteudo)}</pre>
            <div class="modal-footer">
                <a class="btn btn-primary" href="/api/objetos/download?caminho=${encodeURIComponent(dados.caminho)}">
                    ⬇️ Fazer Download do Objeto
                </a>
            </div>
        `;
        modalDetalhes.classList.remove('hidden');
    } catch (err) {
        mostrarToast(err.message, true);
    }
}

async function confirmarExclusaoProduto(idProduto, nomeProduto) {
    const confirmado = window.confirm(
        `Confirma a EXCLUSÃO permanente do produto "${nomeProduto}" (${idProduto}) do bucket fatec-2026-grupo-i no Google Cloud Storage?`
    );
    if (!confirmado) return;

    try {
        const resp = await fetch(`/api/produtos/${encodeURIComponent(idProduto)}`, {
            method: 'DELETE'
        });
        const dados = await resp.json();
        if (!resp.ok) throw new Error(dados.mensagem || 'Erro ao excluir produto.');

        mostrarToast(dados.mensagem);
        await carregarTudoDoBucket();
    } catch (err) {
        mostrarToast(err.message, true);
    }
}

function mostrarToast(mensagem, isErro = false) {
    toastNotificacao.textContent = mensagem;
    toastNotificacao.classList.remove('hidden', 'toast-error');
    if (isErro) {
        toastNotificacao.classList.add('toast-error');
    }
    setTimeout(() => {
        toastNotificacao.classList.add('hidden');
    }, 5500);
}

function escaparHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}
