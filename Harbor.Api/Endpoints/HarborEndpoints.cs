using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Harbor.Api.Data;
using Harbor.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace Harbor.Api.Endpoints;

/// <summary>
/// Rotas da API Harbor.
/// Todas as operações de banco usam o <see cref="HarborDbContext"/> injetado pelo ASP.NET Core:
/// o Entity Framework Core traduz as consultas LINQ para SQL e grava no SQLite (harbor.db).
/// O padrão é sempre o mesmo: carregar a entidade, alterar o que for necessário e chamar
/// <c>SaveChangesAsync</c> para persistir.
/// </summary>
public static class HarborEndpoints
{
    public static void MapHarbor(this WebApplication app)
    {
        var api = app.MapGroup("/api");

        // -------------------------------------------------------------------------
        // Planos: leitura pública, usada pelo app para montar a tela de assinatura.
        // -------------------------------------------------------------------------
        api.MapGet("/planos", async (HarborDbContext db) =>
            await db.Planos.Where(x => x.Ativo).ToListAsync());

        // -------------------------------------------------------------------------
        // Conta e sessão (CREATE/READ de Cliente e RefreshToken).
        // -------------------------------------------------------------------------
        api.MapPost("/contas", CriarConta);              // CREATE de Cliente
        api.MapPost("/sessao", Entrar);                  // READ de Cliente (login)
        api.MapPost("/sessao/renovar", RenovarSessao);   // CREATE de RefreshToken
        api.MapPost("/sessao/sair", Sair);               // UPDATE de RefreshToken (revoga)

        // -------------------------------------------------------------------------
        // Perfil: CRUD completo do Cliente.
        // -------------------------------------------------------------------------
        api.MapGet("/perfil", ObterPerfil);              // READ
        api.MapPut("/perfil", AtualizarPerfil);          // UPDATE
        api.MapDelete("/perfil", ExcluirPerfil);         // DELETE (anonimização, por LGPD)

        // -------------------------------------------------------------------------
        // Catálogo e preferências do leitor.
        // -------------------------------------------------------------------------
        api.MapGet("/livros", ListarLivros);             // READ
        api.MapPost("/preferencias", SalvarPreferencias); // UPDATE de Cliente

        // -------------------------------------------------------------------------
        // Assinatura: 1:1 com Cliente, N:1 com Plano.
        // -------------------------------------------------------------------------
        api.MapGet("/assinatura", ObterAssinatura);                       // READ (Include Plano)
        api.MapPost("/assinatura", Assinar);                              // CREATE
        api.MapPost("/assinatura/{id:int}/pagamento", ConfirmarPagamento); // UPDATE (status)
        api.MapPut("/assinatura/plano", AlterarPlano);                    // UPDATE (PlanoId)
        api.MapPost("/assinatura/cancelar", CancelarAssinatura);           // UPDATE (status)

        // -------------------------------------------------------------------------
        // Pedidos e caixas: N:N entre Cliente e Livro, via Caixa.
        // -------------------------------------------------------------------------
        api.MapGet("/caixas", ListarCaixas);                              // READ (Include Livro)
        api.MapPost("/pedidos", CriarPedido);                             // CREATE de Caixas
        api.MapPost("/pedidos/{id:int}/pagamento", PagarPedido);          // UPDATE de Caixas

        // -------------------------------------------------------------------------
        // Avaliação: N:N entre Cliente e Livro, com nota e comentário.
        // -------------------------------------------------------------------------
        api.MapPost("/avaliacoes", Avaliar);                              // CREATE

        // -------------------------------------------------------------------------
        // Comunidade: READ de Mensagens/Avaliações e CREATE de Mensagem.
        // -------------------------------------------------------------------------
        api.MapGet("/comunidade", ListarComunidade);                      // READ
        api.MapPost("/comunidade", PublicarMensagem);                     // CREATE

        // -------------------------------------------------------------------------
        // Rotas operacionais (protegidas por X-Operations-Key fora do Development).
        // -------------------------------------------------------------------------
        var operacao = app.MapGroup("/api/operacao");
        operacao.AddEndpointFilter(async (context, next) =>
        {
            var env = context.HttpContext.RequestServices.GetRequiredService<IHostEnvironment>();
            var key = Environment.GetEnvironmentVariable("HARBOR_OPERATIONS_KEY");
            if (env.IsDevelopment() && string.IsNullOrWhiteSpace(key)) return await next(context);
            var sent = context.HttpContext.Request.Headers["X-Operations-Key"].ToString();
            return !string.IsNullOrWhiteSpace(key) && CryptographicOperations.FixedTimeEquals(Encoding.UTF8.GetBytes(key), Encoding.UTF8.GetBytes(sent))
                ? await next(context)
                : Results.Unauthorized();
        });
        operacao.MapPost("/livros", CadastrarLivro);                      // CREATE
        operacao.MapPost("/caixas", MontarCaixa);                         // CREATE
        operacao.MapPut("/caixas/{id:int}/envio", RegistrarEnvio);        // UPDATE
        operacao.MapPut("/caixas/{id:int}/status", AtualizarStatusCaixa);// UPDATE
        operacao.MapPost("/curadoria", SugerirCuradoria);                 // READ
        operacao.MapPut("/caixas/{id:int}/tratativa", RegistrarTratativa);// UPDATE
    }

    // ===========================================================================
    // Conta e sessão
    // ===========================================================================

    /// <summary>CREATE de Cliente: valida, grava com bcrypt e já devolve a sessão.</summary>
    private static async Task<IResult> CriarConta(ContaRequest r, HarborDbContext db)
    {
        if (string.IsNullOrWhiteSpace(r.Nome) || !r.Email.Contains('@') || r.Senha.Length < 8 ||
            string.IsNullOrWhiteSpace(r.Endereco) || !r.Consentimento)
            return Results.ValidationProblem(new Dictionary<string, string[]>
            {
                ["cadastro"] = ["Informe nome, e-mail válido, senha com 8 caracteres, endereço e aceite a política de privacidade."]
            });

        var email = r.Email.Trim().ToLowerInvariant();
        var indice = HarborCrypto.EmailIndex(email);
        if (await db.Clientes.AnyAsync(x => x.EmailIndice == indice))
            return Results.Conflict(new { mensagem = "Este e-mail já está cadastrado." });

        var c = new Cliente
        {
            Nome = r.Nome.Trim(),
            Email = email,
            EmailIndice = indice,
            Endereco = r.Endereco.Trim(),
            SenhaHash = BCrypt.Net.BCrypt.HashPassword(r.Senha),
            CriadoEm = DateTime.UtcNow
        };
        db.Clientes.Add(c);                       // EF Core marca a entidade como Added
        await db.SaveChangesAsync();              // INSERT + refresh do Id gerado no SQLite
        return Results.Created("/api/perfil", await NovaSessao(c, db));
    }

    /// <summary>READ de Cliente pelo e-mail (login), com verificação de senha.</summary>
    private static async Task<IResult> Entrar(LoginRequest r, HarborDbContext db)
    {
        var c = await db.Clientes.FirstOrDefaultAsync(x => x.EmailIndice == HarborCrypto.EmailIndex(r.Email));
        if (c is null || c.SenhaHash.Length == 0 || !BCrypt.Net.BCrypt.Verify(r.Senha, c.SenhaHash))
            return Results.Unauthorized();
        return Results.Ok(await NovaSessao(c, db));
    }

    /// <summary>CREATE de RefreshToken: revoga o antigo e emite um novo (rotação).</summary>
    private static async Task<IResult> RenovarSessao(RefreshRequest r, HarborDbContext db)
    {
        if (string.IsNullOrWhiteSpace(r.RefreshToken)) return Results.Unauthorized();
        var hash = HashRefresh(r.RefreshToken);
        var old = await db.RefreshTokens
            .Include(x => x.Cliente)
            .FirstOrDefaultAsync(x => x.TokenHash == hash && !x.Revogada && x.ExpiraEm > DateTime.UtcNow);
        if (old?.Cliente is null) return Results.Unauthorized();
        old.Revogada = true;                      // UPDATE do token antigo
        var next = await NovaSessao(old.Cliente, db);
        await db.SaveChangesAsync();
        return Results.Ok(next);
    }

    /// <summary>UPDATE de RefreshToken: revoga a sessão ativa (logout).</summary>
    private static async Task<IResult> Sair(RefreshRequest r, HarborDbContext db)
    {
        if (!string.IsNullOrWhiteSpace(r.RefreshToken))
        {
            var hash = HashRefresh(r.RefreshToken);
            var session = await db.RefreshTokens.FirstOrDefaultAsync(x => x.TokenHash == hash && !x.Revogada);
            if (session is not null) { session.Revogada = true; await db.SaveChangesAsync(); }
        }
        return Results.NoContent();
    }

    // ===========================================================================
    // Perfil (CRUD do Cliente)
    // ===========================================================================

    /// <summary>READ de Cliente: FindAsync usa a chave primária (Id) como índice.</summary>
    private static async Task<IResult> ObterPerfil(HttpContext http, HarborDbContext db)
    {
        var id = ClienteId(http); if (id is null) return Results.Unauthorized();
        var c = await db.Clientes.FindAsync(id);
        return c is null
            ? Results.NotFound()
            : Results.Ok(new { c.Id, c.Nome, c.Email, c.Endereco, generos = Generos(c.Generos) });
    }

    /// <summary>UPDATE de Cliente: carrega, altera nome/endereço e salva.</summary>
    private static async Task<IResult> AtualizarPerfil(PerfilRequest r, HttpContext http, HarborDbContext db)
    {
        var id = ClienteId(http); if (id is null) return Results.Unauthorized();
        var c = await db.Clientes.FindAsync(id); if (c is null) return Results.NotFound();
        if (string.IsNullOrWhiteSpace(r.Nome) || string.IsNullOrWhiteSpace(r.Endereco))
            return Results.ValidationProblem(new Dictionary<string, string[]>
            {
                ["perfil"] = ["Nome e endereço são obrigatórios."]
            });
        c.Nome = r.Nome.Trim();
        c.Endereco = r.Endereco.Trim();
        await db.SaveChangesAsync();
        return Results.Ok(new { c.Nome, c.Endereco });
    }

    /// <summary>
    /// DELETE de Cliente: por causa da LGPD, o registro não é apagado fisicamente.
    /// Os dados pessoais são substituídos por "Anonimizado" (delete lógico).
    /// </summary>
    private static async Task<IResult> ExcluirPerfil(HttpContext http, HarborDbContext db)
    {
        var id = ClienteId(http); if (id is null) return Results.Unauthorized();
        var c = await db.Clientes.FindAsync(id); if (c is null) return Results.NotFound();
        c.Nome = "Anonimizado";
        c.Email = $"anon-{c.Id}@harbor.invalid";
        c.EmailIndice = HarborCrypto.EmailIndex(c.Email);
        c.Endereco = "";
        c.Generos = "[]";
        c.SenhaHash = "";
        foreach (var session in await db.RefreshTokens.Where(x => x.ClienteId == id).ToListAsync())
            session.Revogada = true;              // UPDATE em lote (relacionamento 1:N)
        foreach (var message in await db.Mensagens.Where(x => x.ClienteId == id).ToListAsync())
        {
            message.Nome = "Leitor anônimo";
            message.Texto = "";
        }
        foreach (var review in await db.Avaliacoes.Where(x => x.ClienteId == id).ToListAsync())
            review.Comentario = "";
        await db.SaveChangesAsync();
        return Results.NoContent();
    }

    // ===========================================================================
    // Catálogo e preferências
    // ===========================================================================

    /// <summary>READ de Livro: AsNoTracking deixa a consulta mais leve (somente leitura).</summary>
    private static async Task<IResult> ListarLivros(HarborDbContext db)
    {
        var livros = await db.Livros
            .AsNoTracking()
            .Select(l => new { l.Id, l.Titulo, l.Autor, l.Sinopse, l.Categoria, l.Estoque })
            .ToListAsync();
        return Results.Ok(livros);
    }

    /// <summary>UPDATE de Cliente: grava os gêneros literários escolhidos (1 a 3).</summary>
    private static async Task<IResult> SalvarPreferencias(PreferenciasRequest r, HttpContext http, HarborDbContext db)
    {
        var id = ClienteId(http); if (id is null) return Results.Unauthorized();
        if (r.Generos is null || r.Generos.Count is < 1 or > 3)
            return Results.ValidationProblem(new Dictionary<string, string[]>
            {
                ["generos"] = ["Escolha de 1 a 3 gêneros."]
            });
        var c = await db.Clientes.FindAsync(id); if (c is null) return Results.NotFound();
        c.Generos = JsonSerializer.Serialize(r.Generos.Distinct());
        await db.SaveChangesAsync();
        return Results.Ok(new { generos = Generos(c.Generos) });
    }

    // ===========================================================================
    // Assinatura (1:1 com Cliente, N:1 com Plano)
    // ===========================================================================

    /// <summary>READ de Assinatura: Include carrega o Plano relacionado em uma única viagem ao banco.</summary>
    private static async Task<IResult> ObterAssinatura(HttpContext http, HarborDbContext db)
    {
        var id = ClienteId(http); if (id is null) return Results.Unauthorized();
        var a = await db.Assinaturas
            .Include(x => x.Plano)
            .FirstOrDefaultAsync(x => x.ClienteId == id);
        return a is null
            ? Results.NotFound()
            : Results.Ok(new
            {
                a.Id,
                a.Status,
                a.CriadaEm,
                a.ProximaRenovacao,
                plano = new { a.Plano!.Id, a.Plano.Nome, a.Plano.Periodicidade, a.Plano.Preco }
            });
    }

    /// <summary>CREATE de Assinatura: fica "Pendente" até o pagamento ser aprovado (RN-08).</summary>
    private static async Task<IResult> Assinar(AssinarRequest r, HttpContext http, HarborDbContext db)
    {
        var id = ClienteId(http); if (id is null) return Results.Unauthorized();
        var plano = await db.Planos.FirstOrDefaultAsync(x => x.Id == r.PlanoId && x.Ativo);
        if (plano is null) return Results.NotFound(new { mensagem = "Plano não encontrado." });
        var existente = await db.Assinaturas.FirstOrDefaultAsync(x => x.ClienteId == id);
        if (existente is not null) return Results.Conflict(new { mensagem = "A conta já possui assinatura." });
        var a = new Assinatura
        {
            ClienteId = id.Value,
            PlanoId = plano.Id,
            Status = "Pendente",
            CriadaEm = DateTime.UtcNow,
            ProximaRenovacao = DateTime.UtcNow.AddMonths(1)
        };
        db.Assinaturas.Add(a);
        await db.SaveChangesAsync();
        return Results.Created("/api/assinatura", new { a.Id, a.Status, plano = plano.Nome, plano.Preco });
    }

    /// <summary>UPDATE de Assinatura: pagamento simulado aprovado muda o status para "Ativa".</summary>
    private static async Task<IResult> ConfirmarPagamento(int id, PagamentoRequest r, HttpContext http, HarborDbContext db)
    {
        // Endpoint acadêmico: aprovação simulada, sem provedor de pagamento real.
        // Substituir por um provedor tokenizado antes de usar em produção.
        var clienteId = ClienteId(http); if (clienteId is null) return Results.Unauthorized();
        var a = await db.Assinaturas.Include(x => x.Plano)
            .FirstOrDefaultAsync(x => x.Id == id && x.ClienteId == clienteId);
        if (a is null) return Results.NotFound();
        if (!r.Aprovado) return Results.BadRequest(new { mensagem = "Pagamento não aprovado; a assinatura permanece pendente." });
        a.Status = "Ativa";
        a.ProximaRenovacao = DateTime.UtcNow.AddMonths(PeriodicidadeMeses(a.Plano!.Periodicidade));
        await db.SaveChangesAsync();
        return Results.Ok(new { a.Id, a.Status, a.ProximaRenovacao, simulado = true });
    }

    /// <summary>UPDATE de Assinatura: troca o plano (o novo valor vale na próxima cobrança).</summary>
    private static async Task<IResult> AlterarPlano(AssinarRequest r, HttpContext http, HarborDbContext db)
    {
        var id = ClienteId(http); if (id is null) return Results.Unauthorized();
        var a = await db.Assinaturas.FirstOrDefaultAsync(x => x.ClienteId == id && x.Status == "Ativa");
        var p = await db.Planos.FirstOrDefaultAsync(x => x.Id == r.PlanoId && x.Ativo);
        if (a is null || p is null) return Results.NotFound(new { mensagem = "Assinatura ativa ou plano não encontrado." });
        a.PlanoId = p.Id;
        await db.SaveChangesAsync();
        return Results.Ok(new { plano = p.Nome, p.Preco, vigencia = "O novo valor aplica-se à próxima cobrança." });
    }

    /// <summary>UPDATE de Assinatura: cancela e impede renovação futura.</summary>
    private static async Task<IResult> CancelarAssinatura(HttpContext http, HarborDbContext db)
    {
        var id = ClienteId(http); if (id is null) return Results.Unauthorized();
        var a = await db.Assinaturas.FirstOrDefaultAsync(x => x.ClienteId == id && x.Status == "Ativa");
        if (a is null) return Results.NotFound(new { mensagem = "Não há assinatura ativa." });
        a.Status = "Cancelada";
        await db.SaveChangesAsync();
        return Results.Ok(new { a.Status, condicao = "A assinatura deixa de renovar. O prazo de arrependimento de 7 dias aplica-se conforme o CDC." });
    }

    // ===========================================================================
    // Pedidos e caixas (N:N Cliente x Livro, via Caixa)
    // ===========================================================================

    /// <summary>READ de Caixa: Include(Livro) resolve o relacionamento N:1 em uma única query.</summary>
    private static async Task<IResult> ListarCaixas(HttpContext http, HarborDbContext db)
    {
        var id = ClienteId(http); if (id is null) return Results.Unauthorized();
        var caixas = await db.Caixas
            .Include(x => x.Livro)
            .Where(x => x.ClienteId == id)
            .OrderByDescending(x => x.CriadaEm)
            .Select(x => new
            {
                x.Id,
                x.Status,
                x.CodigoRastreio,
                x.CriadaEm,
                livro = x.Livro == null
                    ? null
                    : new { x.Livro.Id, x.Livro.Titulo, x.Livro.Autor, x.Livro.Sinopse, x.Livro.Categoria, x.Livro.Estoque }
            })
            .ToListAsync();
        return Results.Ok(caixas);
    }

    /// <summary>
    /// CREATE de Caixas a partir do carrinho: cada item vira uma Caixa (N:N Cliente x Livro)
    /// e o estoque do livro é decrementado.
    /// </summary>
    private static async Task<IResult> CriarPedido(PedidoRequest r, HttpContext http, HarborDbContext db)
    {
        var id = ClienteId(http); if (id is null) return Results.Unauthorized();
        if (r.Itens is null || r.Itens.Count == 0)
            return Results.BadRequest(new { mensagem = "Informe ao menos um item no pedido." });
        var pedidoId = (await db.Caixas.MaxAsync(x => (int?)x.PedidoId) ?? 0) + 1;
        var total = 0m;
        var caixas = new List<Caixa>();
        foreach (var item in r.Itens)
        {
            var livro = await db.Livros.FirstOrDefaultAsync(x => x.Id == item.LivroId && x.Estoque >= item.Quantidade);
            if (livro is null) return Results.BadRequest(new { mensagem = "Livro indisponível ou estoque insuficiente." });
            livro.Estoque -= item.Quantidade;                 // UPDATE de Livro
            total += 65.80m * item.Quantidade;
            caixas.Add(new Caixa
            {
                ClienteId = id.Value,
                LivroId = livro.Id,
                PedidoId = pedidoId,
                Status = "Em preparação",
                Responsavel = "Harbor",
                CriadaEm = DateTime.UtcNow
            });
        }
        db.Caixas.AddRange(caixas);
        await db.SaveChangesAsync();
        return Results.Created($"/api/pedidos/{pedidoId}", new { id = pedidoId, status = "Pendente", total });
    }

    /// <summary>UPDATE de Caixas: pagamento simulado aprovado marca todas as caixas do pedido como "Pago".</summary>
    private static async Task<IResult> PagarPedido(int id, PagamentoRequest r, HttpContext http, HarborDbContext db)
    {
        // Endpoint acadêmico: aprovação simulada, sem provedor de pagamento real.
        var clienteId = ClienteId(http); if (clienteId is null) return Results.Unauthorized();
        var caixas = await db.Caixas.Where(x => x.PedidoId == id && x.ClienteId == clienteId).ToListAsync();
        if (caixas.Count == 0) return Results.NotFound(new { mensagem = "Pedido não encontrado." });
        if (!r.Aprovado) return Results.BadRequest(new { mensagem = "Pagamento não aprovado; o pedido permanece pendente." });
        foreach (var caixa in caixas) caixa.Status = "Pago";
        await db.SaveChangesAsync();
        return Results.Ok(new { id, status = "Pago", simulado = true });
    }

    // ===========================================================================
    // Avaliação (N:N Cliente x Livro, com nota e comentário)
    // ===========================================================================

    /// <summary>CREATE de Avaliacao: só permite avaliar livro entregue, com nota de 1 a 5.</summary>
    private static async Task<IResult> Avaliar(AvaliacaoRequest r, HttpContext http, HarborDbContext db)
    {
        var id = ClienteId(http); if (id is null) return Results.Unauthorized();
        var entregue = await db.Caixas.AnyAsync(x => x.ClienteId == id && x.LivroId == r.LivroId && x.Status == "Entregue");
        if (!entregue || r.Nota is < 1 or > 5)
            return Results.BadRequest(new { mensagem = "Avalie de 1 a 5 somente um livro recebido." });
        var av = new Avaliacao
        {
            ClienteId = id.Value,
            LivroId = r.LivroId,
            Nota = r.Nota,
            Comentario = r.Comentario?.Trim() ?? "",
            CriadaEm = DateTime.UtcNow
        };
        db.Avaliacoes.Add(av);
        await db.SaveChangesAsync();
        return Results.Created($"/api/avaliacoes/{av.Id}", av);
    }

    // ===========================================================================
    // Comunidade
    // ===========================================================================

    /// <summary>READ de Comunidade: mensagens + avaliações em uma única listagem (joins LINQ).</summary>
    private static async Task<IResult> ListarComunidade(HarborDbContext db)
    {
        var mensagens = await db.Mensagens
            .AsNoTracking()
            .OrderByDescending(x => x.CriadaEm)
            .Take(100)
            .Select(x => new ComunidadeItem(x.Id, "Mensagem", x.Nome, x.Texto, x.CriadaEm))
            .ToListAsync();

        var avaliacoesRaw = await (from a in db.Avaliacoes.AsNoTracking()
                                   join c in db.Clientes.AsNoTracking() on a.ClienteId equals c.Id
                                   join l in db.Livros.AsNoTracking() on a.LivroId equals l.Id
                                   orderby a.CriadaEm descending
                                   select new { a.Id, c.Nome, l.Titulo, a.Nota, a.Comentario, a.CriadaEm })
            .Take(100)
            .ToListAsync();

        var avaliacoes = avaliacoesRaw.Select(a =>
            new ComunidadeItem(-a.Id, "Avaliação", a.Nome, $"{a.Titulo} • {a.Nota}/5 — {a.Comentario}", a.CriadaEm));

        return Results.Ok(mensagens.Concat(avaliacoes).OrderBy(x => x.CriadaEm).TakeLast(100));
    }

    /// <summary>CREATE de Mensagem: somente assinantes ativos podem publicar.</summary>
    private static async Task<IResult> PublicarMensagem(MensagemRequest r, HttpContext http, HarborDbContext db)
    {
        var id = ClienteId(http); if (id is null) return Results.Unauthorized();
        if (!await db.Assinaturas.AnyAsync(x => x.ClienteId == id && x.Status == "Ativa")) return Results.Forbid();
        if (string.IsNullOrWhiteSpace(r.Texto) || r.Texto.Length > 1000)
            return Results.BadRequest(new { mensagem = "A mensagem deve ter até 1000 caracteres." });
        var c = await db.Clientes.FindAsync(id);
        var m = new Mensagem
        {
            ClienteId = id.Value,
            Nome = c!.Nome,
            Texto = r.Texto.Trim(),
            CriadaEm = DateTime.UtcNow
        };
        db.Mensagens.Add(m);
        await db.SaveChangesAsync();
        return Results.Created($"/api/comunidade/{m.Id}", m);
    }

    // ===========================================================================
    // Rotas operacionais (estoque, curadoria, envio e tratativas)
    // ===========================================================================

    /// <summary>CREATE de Livro (operação interna de catálogo).</summary>
    private static async Task<IResult> CadastrarLivro(LivroRequest r, HarborDbContext db)
    {
        if (r.Estoque < 0 || string.IsNullOrWhiteSpace(r.Titulo)) return Results.BadRequest();
        var l = new Livro
        {
            Titulo = r.Titulo.Trim(),
            Autor = r.Autor.Trim(),
            Sinopse = r.Sinopse.Trim(),
            Categoria = r.Categoria.Trim(),
            Estoque = r.Estoque
        };
        db.Livros.Add(l);
        await db.SaveChangesAsync();
        return Results.Created($"/api/livros/{l.Id}", l);
    }

    /// <summary>CREATE de Caixa: separa um livro do estoque para a assinatura ativa do cliente.</summary>
    private static async Task<IResult> MontarCaixa(MontarCaixaRequest r, HarborDbContext db)
    {
        var sub = await db.Assinaturas.Include(x => x.Cliente)
            .FirstOrDefaultAsync(x => x.ClienteId == r.ClienteId && x.Status == "Ativa");
        if (sub is null) return Results.BadRequest(new { mensagem = "É necessária uma assinatura ativa." });

        Livro? livro;
        if (r.LivroId > 0)
            livro = await db.Livros.FirstOrDefaultAsync(x => x.Id == r.LivroId && x.Estoque > 0);
        else
        {
            var generos = Generos(sub.Cliente!.Generos);
            livro = await db.Livros
                .Where(x => x.Estoque > 0 && generos.Contains(x.Categoria))
                .OrderBy(x => x.Id)
                .FirstOrDefaultAsync();
        }
        if (livro is null) return Results.Conflict(new { mensagem = "Não há obra compatível em estoque; sinalizar reposição." });

        livro.Estoque--;                             // UPDATE de Livro
        var caixa = new Caixa
        {
            ClienteId = r.ClienteId,
            LivroId = livro.Id,
            Responsavel = r.Responsavel.Trim(),
            CriadaEm = DateTime.UtcNow
        };
        db.Caixas.Add(caixa);
        await db.SaveChangesAsync();
        return Results.Created($"/api/caixas/{caixa.Id}", new { caixa.Id, caixa.Status, livro = livro.Titulo, responsavel = r.Responsavel });
    }

    /// <summary>UPDATE de Caixa: registra o código de rastreamento e muda o status para "Em transporte".</summary>
    private static async Task<IResult> RegistrarEnvio(int id, EnvioRequest r, HarborDbContext db)
    {
        var c = await db.Caixas.FindAsync(id); if (c is null) return Results.NotFound();
        if (string.IsNullOrWhiteSpace(r.CodigoRastreio)) return Results.BadRequest();
        c.CodigoRastreio = r.CodigoRastreio.Trim();
        c.Status = "Em transporte";
        await db.SaveChangesAsync();
        return Results.Ok(c);
    }

    /// <summary>UPDATE de Caixa: troca livremente o status (ex.: "Entregue").</summary>
    private static async Task<IResult> AtualizarStatusCaixa(int id, StatusRequest r, HarborDbContext db)
    {
        var c = await db.Caixas.FindAsync(id); if (c is null) return Results.NotFound();
        c.Status = r.Status;
        await db.SaveChangesAsync();
        return Results.Ok(c);
    }

    /// <summary>READ de curadoria: sugere o próximo livro compatível com os gêneros do assinante.</summary>
    private static async Task<IResult> SugerirCuradoria(CuradoriaRequest r, HarborDbContext db)
    {
        var assinatura = await db.Assinaturas.Include(x => x.Cliente)
            .FirstOrDefaultAsync(x => x.ClienteId == r.ClienteId && x.Status == "Ativa");
        if (assinatura is null) return Results.BadRequest(new { mensagem = "É necessária uma assinatura ativa." });
        var generos = Generos(assinatura.Cliente!.Generos);
        var livro = await db.Livros
            .Where(x => x.Estoque > 0 && generos.Contains(x.Categoria))
            .OrderBy(x => x.Id)
            .FirstOrDefaultAsync();
        if (livro is null) return Results.Conflict(new { mensagem = "Não há obra compatível em estoque; reposição necessária." });
        return Results.Ok(new { livro.Id, livro.Titulo, livro.Autor, livro.Sinopse, livro.Categoria });
    }

    /// <summary>UPDATE de Caixa: registra a tratativa de uma entrega problemática (reposição ou reembolso).</summary>
    private static async Task<IResult> RegistrarTratativa(int id, TratativaRequest r, HarborDbContext db)
    {
        var caixa = await db.Caixas.FindAsync(id); if (caixa is null) return Results.NotFound();
        if (r.Resultado is not ("Reposição" or "Reembolso"))
            return Results.BadRequest(new { mensagem = "Informe Reposição ou Reembolso." });
        caixa.Status = r.Resultado == "Reposição" ? "Reposição em andamento" : "Reembolsada";
        await db.SaveChangesAsync();
        return Results.Ok(new { caixa.Id, caixa.Status, tratativa = r.Detalhes });
    }

    // ===========================================================================
    // Helpers
    // ===========================================================================

    /// <summary>CREATE de RefreshToken: gera o token de 30 dias e já devolve a sessão.</summary>
    private static async Task<object> NovaSessao(Cliente cliente, HarborDbContext db)
    {
        var refresh = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32))
            .TrimEnd('=').Replace('+', '-').Replace('/', '_');
        db.RefreshTokens.Add(new RefreshToken
        {
            ClienteId = cliente.Id,
            TokenHash = HashRefresh(refresh),
            CriadaEm = DateTime.UtcNow,
            ExpiraEm = DateTime.UtcNow.AddDays(30)
        });
        await db.SaveChangesAsync();
        return new { token = Token(cliente.Id), refreshToken = refresh, cliente = new { cliente.Id, cliente.Nome, cliente.Email } };
    }

    /// <summary>Hash SHA-256 do refresh token (o banco guarda só o hash).</summary>
    private static string HashRefresh(string refresh) =>
        Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(refresh)));

    /// <summary>Token de acesso (15 min) assinado com HMAC-SHA-256.</summary>
    private static string Token(int id)
    {
        var exp = DateTimeOffset.UtcNow.AddMinutes(15).ToUnixTimeSeconds();
        var payload = $"{id}:{exp}";
        var key = Encoding.UTF8.GetBytes(Environment.GetEnvironmentVariable("HARBOR_TOKEN_KEY") ?? "harbor-dev-key-change-before-deploy-32");
        var sig = HMACSHA256.HashData(key, Encoding.UTF8.GetBytes(payload));
        return Convert.ToBase64String(Encoding.UTF8.GetBytes(payload + ":" + Convert.ToHexString(sig)));
    }

    /// <summary>Recupera o Id do cliente a partir do cabeçalho Authorization (Bearer).</summary>
    private static int? ClienteId(HttpContext http)
    {
        var raw = http.Request.Headers.Authorization.ToString();
        if (!raw.StartsWith("Bearer ")) return null;
        try
        {
            var decoded = Encoding.UTF8.GetString(Convert.FromBase64String(raw[7..])).Split(':');
            var payload = $"{decoded[0]}:{decoded[1]}";
            var key = Encoding.UTF8.GetBytes(Environment.GetEnvironmentVariable("HARBOR_TOKEN_KEY") ?? "harbor-dev-key-change-before-deploy-32");
            var expected = Convert.ToHexString(HMACSHA256.HashData(key, Encoding.UTF8.GetBytes(payload)));
            return long.Parse(decoded[1]) > DateTimeOffset.UtcNow.ToUnixTimeSeconds() &&
                   CryptographicOperations.FixedTimeEquals(Encoding.UTF8.GetBytes(expected), Encoding.UTF8.GetBytes(decoded[2]))
                ? int.Parse(decoded[0])
                : null;
        }
        catch { return null; }
    }

    /// <summary>Desserializa os gêneros salvos como JSON na coluna Generos.</summary>
    private static string[] Generos(string json) => JsonSerializer.Deserialize<string[]>(json) ?? [];

    /// <summary>Converte a periodicidade do plano em meses (1, 3 ou 12).</summary>
    private static int PeriodicidadeMeses(string periodicidade) =>
        periodicidade.Equals("Anual", StringComparison.OrdinalIgnoreCase) ? 12 :
        periodicidade.Equals("Trimestral", StringComparison.OrdinalIgnoreCase) ? 3 : 1;
}

// ===========================================================================
// Contratos de entrada (records imutáveis, desserializadas do JSON do request)
// ===========================================================================
public sealed record ContaRequest(string Nome, string Email, string Senha, string Endereco, bool Consentimento);
public sealed record LoginRequest(string Email, string Senha);
public sealed record RefreshRequest(string RefreshToken);
public sealed record PerfilRequest(string Nome, string Endereco);
public sealed record PreferenciasRequest(List<string>? Generos);
public sealed record AssinarRequest(int PlanoId);
public sealed record LivroRequest(string Titulo, string Autor, string Sinopse, string Categoria, int Estoque);
public sealed record MontarCaixaRequest(int ClienteId, int LivroId, string Responsavel);
public sealed record EnvioRequest(string CodigoRastreio);
public sealed record StatusRequest(string Status);
public sealed record AvaliacaoRequest(int LivroId, int Nota, string? Comentario);
public sealed record MensagemRequest(string Texto);
public sealed record PedidoRequest(string Cep, string Cupom, string FormaPagamento, List<PedidoItemRequest> Itens);
public sealed record PedidoItemRequest(int LivroId, int Quantidade);
public sealed record PagamentoRequest(bool Aprovado);
public sealed record CuradoriaRequest(int ClienteId);
public sealed record TratativaRequest(string Resultado, string Detalhes);
public sealed record ComunidadeItem(int Id, string Tipo, string Nome, string Texto, DateTime CriadaEm);
