// Estado da aplicação no Frontend
let listaProdutosCache = [];
let idProdutoParaUploadImagem = null;
let idProdutoAbertoDetalhes = null;

// Elementos do DOM
const gridProdutos = document.getElementById('gridProdutos');
const listaObjetosImagens = document.getElementById('listaObjetosImagens');
const statTotalProdutos = document.getElementById('statTotalProdutos');
const statTotalImagens = document.getElementById('statTotalImagens');
const statBancoDados = document.getElementById('statBancoDados');
const inputBusca = document.getElementById('inputBusca');
const toastNotificacao = document.getElementById('toastNotificacao');

// Modal de Cadastro
const modalCadastro = document.getElementById('modalCadastro');
const formCadastroProduto = document.getElementById('formCadastroProduto');
const btnSalvarProduto = document.getElementById('btnSalvarProduto');

// Modal de Detalhes e Galeria
const modalDetalhes = document.getElementById('modalDetalhes');
const tituloModalDetalhes = document.getElementById('tituloModalDetalhes');
const corpoModalDetalhes = document.getElementById('corpoModalDetalhes');

// Ações Globais
const btnCargaDemo = document.getElementById('btnCargaDemo');
const inputUploadImagemRapido = document.getElementById('inputUploadImagemRapido');

document.addEventListener('DOMContentLoaded', () => {
    carregarTudo();

    document.getElementById('btnRecarregar').addEventListener('click', () => {
        mostrarToast('Atualizando dados...');
        carregarTudo();
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
        idProdutoAbertoDetalhes = null;
    });

    inputBusca.addEventListener('input', () => {
        renderizarVitrine(listaProdutosCache);
    });

    formCadastroProduto.addEventListener('submit', salvarNovoProduto);

    btnCargaDemo.addEventListener('click', executarCargaDemonstracao);

    inputUploadImagemRapido.addEventListener('change', async (e) => {
        const arquivo = e.target.files[0];
        if (!arquivo || !idProdutoParaUploadImagem) return;
        try {
            mostrarToast(`Enviando foto para o produto ${idProdutoParaUploadImagem}...`);
            await enviarImagemProduto(idProdutoParaUploadImagem, arquivo, false);
            mostrarToast('Foto enviada e vinculada com sucesso.');
            await carregarTudo();
        } catch (err) {
            mostrarToast(err.message || 'Erro ao enviar imagem.', true);
        } finally {
            inputUploadImagemRapido.value = '';
            idProdutoParaUploadImagem = null;
        }
    });
});

async function carregarTudo() {
    await Promise.all([
        carregarInfo(),
        carregarProdutos(),
        carregarObjetosDoBucket()
    ]);
}

async function carregarInfo() {
    try {
        const resp = await fetch('/api/info');
        if (!resp.ok) return;
        const info = await resp.json();
        if (statBancoDados && info.bancoDados) {
            statBancoDados.textContent = info.bancoDados;
        }
    } catch (ignored) {
    }
}

async function carregarProdutos() {
    try {
        const resp = await fetch('/api/produtos');
        const dados = await resp.json();
        if (!resp.ok) throw new Error(dados.mensagem || 'Erro ao listar produtos do banco');

        listaProdutosCache = dados;
        statTotalProdutos.textContent = `${dados.length} ${dados.length === 1 ? 'produto' : 'produtos'}`;
        renderizarVitrine(dados);
    } catch (err) {
        gridProdutos.innerHTML = `<div class="empty-state">Erro ao comunicar com o backend Java: ${escaparHtml(err.message)}</div>`;
    }
}

async function carregarObjetosDoBucket() {
    try {
        const resp = await fetch('/api/objetos');
        const dados = await resp.json();
        if (!resp.ok) throw new Error(dados.mensagem || 'Erro ao listar objetos');

        const objsImg = dados.objetosImagens || [];
        statTotalImagens.textContent = `${objsImg.length} ${objsImg.length === 1 ? 'foto' : 'fotos'}`;

        if (objsImg.length === 0) {
            listaObjetosImagens.innerHTML = `<li class="object-item">Nenhuma imagem em <code>imagens/</code> no momento</li>`;
        } else {
            listaObjetosImagens.innerHTML = objsImg.map(caminho => {
                const urlPublica = `${dados.urlPublicaBase}${caminho}`;
                return `
                    <li class="object-item">
                        <code>${caminho}</code>
                        <div class="object-actions">
                            <a class="btn btn-secondary btn-sm" href="/api/objetos/download?caminho=${encodeURIComponent(caminho)}">⬇️ Baixar</a>
                            <a class="btn btn-secondary btn-sm" href="${urlPublica}" target="_blank" rel="noopener">Abrir Foto ↗</a>
                        </div>
                    </li>
                `;
            }).join('');
        }
    } catch (err) {
        listaObjetosImagens.innerHTML = `<li>Erro: ${escaparHtml(err.message)}</li>`;
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
                <h3>Nenhum produto encontrado</h3>
                <p>Tente outro termo ou cadastre um novo produto (ou use o botão "Carga Demo").</p>
            </div>
        `;
        return;
    }

    gridProdutos.innerHTML = filtrados.map(p => {
        const precoFormatado = Number(p.preco || 0).toLocaleString('pt-BR', {
            style: 'currency',
            currency: 'BRL'
        });

        const totalFotos = p.totalImagens || (p.imagens ? p.imagens.length : 0);
        const urlCapa = p.urlImagemPrincipal;

        const htmlImagem = urlCapa
            ? `<img src="${urlCapa}" alt="${escaparHtml(p.nome)}" loading="lazy">`
            : `<div class="no-image-placeholder">
                   <p>Sem foto de capa</p>
                   <button class="btn btn-secondary btn-sm" onclick="dispararUploadImagem('${p.id}')">
                       + Adicionar foto
                   </button>
               </div>`;

        return `
            <article class="product-card">
                <div class="product-image-box">
                    <span class="badge-id">${escaparHtml(p.id)}</span>
                    <span class="badge-category">${escaparHtml(p.categoria)}</span>
                    <span class="badge-photos">📷 ${totalFotos} ${totalFotos === 1 ? 'foto' : 'fotos'}</span>
                    ${htmlImagem}
                </div>
                <div class="product-body">
                    <h3 class="product-title">${escaparHtml(p.nome)}</h3>
                    <div class="product-price">${precoFormatado}</div>
                    <p class="product-desc">${escaparHtml(p.descricao)}</p>
                </div>
                <div class="product-actions">
                    <button class="btn btn-primary btn-sm" onclick="abrirDetalhesProduto('${p.id}')">
                        🔍 Ver detalhes & galeria
                    </button>
                    <button class="btn btn-secondary btn-sm" onclick="dispararUploadImagem('${p.id}')">
                        + Foto
                    </button>
                    <button class="btn btn-danger btn-sm" onclick="confirmarExclusaoProduto('${p.id}', '${escaparHtml(p.nome)}')">
                        Excluir
                    </button>
                </div>
            </article>
        `;
    }).join('');
}

async function abrirDetalhesProduto(idProduto) {
    idProdutoAbertoDetalhes = idProduto;
    try {
        const resp = await fetch(`/api/produtos/${encodeURIComponent(idProduto)}`);
        const p = await resp.json();
        if (!resp.ok) throw new Error(p.mensagem || 'Produto não encontrado.');

        const precoFormatado = Number(p.preco || 0).toLocaleString('pt-BR', {
            style: 'currency',
            currency: 'BRL'
        });

        tituloModalDetalhes.textContent = `${p.id} — ${p.nome}`;

        const imagens = p.imagens || [];
        const imagemPrincipal = imagens.find(img => img.ehPrincipal) || imagens[0] || null;

        let htmlCapa = '';
        if (imagemPrincipal) {
            htmlCapa = `
                <div class="featured-cover-box">
                    <div class="cover-image-container">
                        <img src="${imagemPrincipal.urlPublica}" alt="${escaparHtml(p.nome)} - Capa">
                        <span class="cover-badge">★ Imagem Principal (Capa do Catálogo)</span>
                    </div>
                    <div class="cover-info">
                        <p><strong>Arquivo:</strong> <code>${escaparHtml(imagemPrincipal.nomeArquivoOriginal || 'imagem')}</code></p>
                        <p><strong>Caminho GCS:</strong> <code>${escaparHtml(imagemPrincipal.caminhoObjeto)}</code></p>
                        <div class="cover-actions">
                            <a class="btn btn-secondary btn-sm" href="${imagemPrincipal.urlPublica}" target="_blank" rel="noopener">Abrir URL Pública ↗</a>
                            <a class="btn btn-secondary btn-sm" href="/api/objetos/download?caminho=${encodeURIComponent(imagemPrincipal.caminhoObjeto)}">⬇️ Baixar Imagem</a>
                        </div>
                    </div>
                </div>
            `;
        } else {
            htmlCapa = `
                <div class="no-image-banner">
                    <p>Este produto ainda não possui nenhuma imagem vinculada.</p>
                </div>
            `;
        }

        let htmlGaleria = '';
        if (imagens.length === 0) {
            htmlGaleria = `<p class="gallery-empty">Nenhuma foto adicionada até o momento.</p>`;
        } else {
            htmlGaleria = `
                <div class="gallery-grid">
                    ${imagens.map(img => {
                        const isCapa = img.ehPrincipal;
                        return `
                            <div class="gallery-item ${isCapa ? 'is-capa' : ''}">
                                <div class="gallery-thumb-container">
                                    <img src="${img.urlPublica}" alt="Foto ${img.id}" loading="lazy">
                                    ${isCapa ? '<span class="gallery-capa-tag">★ CAPA</span>' : ''}
                                </div>
                                <div class="gallery-item-body">
                                    <span class="gallery-filename" title="${escaparHtml(img.nomeArquivoOriginal)}">${escaparHtml(img.nomeArquivoOriginal || 'foto')}</span>
                                    <div class="gallery-actions">
                                        ${!isCapa ? `<button class="btn btn-secondary btn-sm" onclick="definirComoCapa('${p.id}', ${img.id})">★ Definir Capa</button>` : ''}
                                        <a class="btn btn-secondary btn-sm" href="/api/objetos/download?caminho=${encodeURIComponent(img.caminhoObjeto)}">⬇️ Baixar</a>
                                        <a class="btn btn-secondary btn-sm" href="${img.urlPublica}" target="_blank" rel="noopener">URL ↗</a>
                                        <button class="btn btn-danger btn-sm" onclick="apagarFotoIndividual('${p.id}', ${img.id})">🗑️ Apagar</button>
                                    </div>
                                </div>
                            </div>
                        `;
                    }).join('')}
                </div>
            `;
        }

        corpoModalDetalhes.innerHTML = `
            <div class="product-detail-section">
                <div class="detail-grid-info">
                    <div><strong>Categoria:</strong> ${escaparHtml(p.categoria)}</div>
                    <div><strong>Preço:</strong> <span class="detail-price">${precoFormatado}</span></div>
                    <div><strong>Armazenamento:</strong> SQLite local (ecommerce.db)</div>
                    <div><strong>Total de Fotos:</strong> ${imagens.length} no Google Cloud Storage</div>
                </div>
                <p class="detail-desc"><strong>Descrição:</strong> ${escaparHtml(p.descricao)}</p>
            </div>

            <div class="product-detail-section">
                <h4 class="section-title">Imagem Principal em Destaque</h4>
                ${htmlCapa}
            </div>

            <div class="product-detail-section">
                <h4 class="section-title">Galeria de Fotos do Produto (${imagens.length})</h4>
                ${htmlGaleria}
            </div>

            <div class="product-detail-section upload-box">
                <h4 class="section-title">+ Adicionar Nova Foto a este Produto</h4>
                <div class="upload-inline-form">
                    <input type="file" id="inputFotoDetalhe" accept=".png,.jpg,.jpeg,.webp,.gif" class="file-input">
                    <label class="checkbox-label">
                        <input type="checkbox" id="checkMarcarCapa">
                        Definir esta nova foto como Imagem Principal (Capa)
                    </label>
                    <button class="btn btn-primary" onclick="enviarFotoPeloDetalhe('${p.id}')">
                        Upload para o GCS
                    </button>
                </div>
            </div>
        `;

        modalDetalhes.classList.remove('hidden');
    } catch (err) {
        mostrarToast(err.message, true);
    }
}

async function definirComoCapa(idProduto, imagemId) {
    try {
        mostrarToast('Atualizando imagem principal...');
        const resp = await fetch(`/api/produtos/${encodeURIComponent(idProduto)}/imagens/${imagemId}/principal`, {
            method: 'PUT'
        });
        const dados = await resp.json();
        if (!resp.ok) throw new Error(dados.mensagem || 'Erro ao definir capa.');

        mostrarToast('Imagem definida como Capa com sucesso!');
        await carregarTudo();
        if (idProdutoAbertoDetalhes === idProduto) {
            await abrirDetalhesProduto(idProduto);
        }
    } catch (err) {
        mostrarToast(err.message, true);
    }
}

async function apagarFotoIndividual(idProduto, imagemId) {
    const confirmou = window.confirm('Tem certeza que deseja apagar esta imagem do Google Cloud Storage e do banco local?');
    if (!confirmou) return;

    try {
        mostrarToast('Apagando imagem do GCS...');
        const resp = await fetch(`/api/produtos/${encodeURIComponent(idProduto)}/imagens/${imagemId}`, {
            method: 'DELETE'
        });
        const dados = await resp.json();
        if (!resp.ok) throw new Error(dados.mensagem || 'Erro ao excluir imagem.');

        mostrarToast('Imagem apagada com sucesso!');
        await carregarTudo();
        if (idProdutoAbertoDetalhes === idProduto) {
            await abrirDetalhesProduto(idProduto);
        }
    } catch (err) {
        mostrarToast(err.message, true);
    }
}

async function enviarFotoPeloDetalhe(idProduto) {
    const input = document.getElementById('inputFotoDetalhe');
    const checkCapa = document.getElementById('checkMarcarCapa');
    const arquivo = input ? input.files[0] : null;

    if (!arquivo) {
        mostrarToast('Selecione um arquivo de imagem para enviar.', true);
        return;
    }

    const marcarCapa = checkCapa ? checkCapa.checked : false;

    try {
        mostrarToast(`Enviando ${arquivo.name} para o Google Cloud Storage...`);
        await enviarImagemProduto(idProduto, arquivo, marcarCapa);
        mostrarToast('Foto enviada com sucesso ao GCS!');
        await carregarTudo();
        await abrirDetalhesProduto(idProduto);
    } catch (err) {
        mostrarToast(err.message, true);
    }
}

async function salvarNovoProduto(e) {
    e.preventDefault();
    const nome = document.getElementById('campoNome').value.trim();
    const categoria = document.getElementById('campoCategoria').value.trim();
    const preco = document.getElementById('campoPreco').value.trim();
    const descricao = document.getElementById('campoDescricao').value.trim();
    const arquivoImagem = document.getElementById('campoImagem').files[0];

    btnSalvarProduto.disabled = true;
    btnSalvarProduto.textContent = 'Salvando no SQLite...';

    try {
        const resp = await fetch('/api/produtos', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ nome, categoria, preco, descricao })
        });
        const produtoCriado = await resp.json();
        if (!resp.ok) throw new Error(produtoCriado.mensagem || 'Erro ao cadastrar produto.');

        if (arquivoImagem) {
            mostrarToast('Enviando imagem para o GCS...');
            await enviarImagemProduto(produtoCriado.id, arquivoImagem, true);
        }

        modalCadastro.classList.add('hidden');
        mostrarToast('Produto salvo no banco local SQLite com sucesso.');
        await carregarTudo();
    } catch (err) {
        mostrarToast(err.message, true);
    } finally {
        btnSalvarProduto.disabled = false;
        btnSalvarProduto.textContent = 'Salvar no Banco Local';
    }
}

async function enviarImagemProduto(idProduto, arquivo, principal = false) {
    const url = `/api/produtos/${encodeURIComponent(idProduto)}/imagens?nomeArquivo=${encodeURIComponent(arquivo.name)}&principal=${principal}`;
    const resp = await fetch(url, {
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

async function confirmarExclusaoProduto(idProduto, nomeProduto) {
    const confirmado = window.confirm(
        `Confirma a EXCLUSÃO do produto "${nomeProduto}" (${idProduto}) do banco SQLite e de TODAS as suas fotos no bucket GCS fatec-2026-grupo-i?`
    );
    if (!confirmado) return;

    try {
        mostrarToast(`Excluindo ${idProduto} e suas fotos...`);
        const resp = await fetch(`/api/produtos/${encodeURIComponent(idProduto)}`, {
            method: 'DELETE'
        });
        const dados = await resp.json();
        if (!resp.ok) throw new Error(dados.mensagem || 'Erro ao excluir produto.');

        mostrarToast(dados.mensagem);
        if (idProdutoAbertoDetalhes === idProduto) {
            modalDetalhes.classList.add('hidden');
            idProdutoAbertoDetalhes = null;
        }
        await carregarTudo();
    } catch (err) {
        mostrarToast(err.message, true);
    }
}

async function executarCargaDemonstracao() {
    const confirmou = window.confirm(
        "Deseja gerar a Carga de Demonstração (10 produtos no SQLite e 30 fotos reais no bucket fatec-2026-grupo-i no GCS)?"
    );
    if (!confirmou) return;

    btnCargaDemo.disabled = true;
    btnCargaDemo.textContent = '⏳ Gerando 30 fotos no GCS...';
    mostrarToast('Executando Carga Demo: cadastrando 10 produtos e enviando 30 imagens reais para o GCS...');

    try {
        const resp = await fetch('/api/demo/seed', { method: 'POST' });
        const dados = await resp.json();
        if (!resp.ok) throw new Error(dados.mensagem || 'Falha ao executar carga demo.');

        mostrarToast(dados.mensagem);
        await carregarTudo();
    } catch (err) {
        mostrarToast(err.message, true);
    } finally {
        btnCargaDemo.disabled = false;
        btnCargaDemo.textContent = '⚡ Carga Demo (10 produtos x 3 fotos)';
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
    }, 6000);
}

function escaparHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}
