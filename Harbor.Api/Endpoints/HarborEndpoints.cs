using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Harbor.Api.Data;
using Harbor.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace Harbor.Api.Endpoints;

public static class HarborEndpoints
{
    public static void MapHarbor(this WebApplication app)
    {
        var api = app.MapGroup("/api");
        api.MapGet("/planos", async (HarborDbContext db) => await db.Planos.Where(x => x.Ativo).ToListAsync());
        api.MapPost("/contas", CriarConta);
        api.MapPost("/sessao", Entrar);
        api.MapPost("/sessao/renovar", async (RefreshRequest r, HarborDbContext db) =>
        {
            if (string.IsNullOrWhiteSpace(r.RefreshToken)) return Results.Unauthorized();
            var hash = HashRefresh(r.RefreshToken);
            var old = await db.RefreshTokens.Include(x => x.Cliente).FirstOrDefaultAsync(x => x.TokenHash == hash && !x.Revogada && x.ExpiraEm > DateTime.UtcNow);
            if (old?.Cliente is null) return Results.Unauthorized();
            old.Revogada = true;
            var next = await NovaSessao(old.Cliente, db);
            await db.SaveChangesAsync();
            return Results.Ok(next);
        });
        api.MapPost("/sessao/sair", async (RefreshRequest r, HarborDbContext db) =>
        {
            if (!string.IsNullOrWhiteSpace(r.RefreshToken))
            {
                var hash = HashRefresh(r.RefreshToken);
                var session = await db.RefreshTokens.FirstOrDefaultAsync(x => x.TokenHash == hash && !x.Revogada);
                if (session is not null) { session.Revogada = true; await db.SaveChangesAsync(); }
            }
            return Results.NoContent();
        });
        api.MapGet("/perfil", async (HttpContext http, HarborDbContext db) =>
        {
            var id = ClienteId(http); if (id is null) return Results.Unauthorized();
            var c = await db.Clientes.FindAsync(id); return c is null ? Results.NotFound() : Results.Ok(new { c.Id, c.Nome, c.Email, c.Endereco, generos = Generos(c.Generos) });
        });
        api.MapPut("/perfil", async (PerfilRequest r, HttpContext http, HarborDbContext db) =>
        {
            var id = ClienteId(http); if (id is null) return Results.Unauthorized();
            var c = await db.Clientes.FindAsync(id); if (c is null) return Results.NotFound();
            if (string.IsNullOrWhiteSpace(r.Nome) || string.IsNullOrWhiteSpace(r.Endereco)) return Results.ValidationProblem(new Dictionary<string, string[]> { ["perfil"] = ["Nome e endereço são obrigatórios."] });
            c.Nome = r.Nome.Trim(); c.Endereco = r.Endereco.Trim(); await db.SaveChangesAsync(); return Results.Ok(new { c.Nome, c.Endereco });
        });
        api.MapDelete("/perfil", async (HttpContext http, HarborDbContext db) =>
        {
            var id = ClienteId(http); if (id is null) return Results.Unauthorized();
            var c = await db.Clientes.FindAsync(id); if (c is null) return Results.NotFound();
            c.Nome = "Anonimizado"; c.Email = $"anon-{c.Id}@harbor.invalid"; c.EmailIndice = HarborCrypto.EmailIndex(c.Email); c.Endereco = ""; c.Generos = "[]"; c.SenhaHash = "";
            foreach (var session in await db.RefreshTokens.Where(x => x.ClienteId == id).ToListAsync()) session.Revogada = true;
            foreach (var message in await db.Mensagens.Where(x => x.ClienteId == id).ToListAsync()) { message.Nome = "Leitor anônimo"; message.Texto = ""; }
            foreach (var review in await db.Avaliacoes.Where(x => x.ClienteId == id).ToListAsync()) review.Comentario = "";
            await db.SaveChangesAsync(); return Results.NoContent();
        });
        api.MapGet("/livros", async (HarborDbContext db) => await db.Livros.AsNoTracking().Select(l => new { l.Id, l.Titulo, l.Autor, l.Sinopse, l.Categoria, l.Estoque }).ToListAsync());
        api.MapPost("/preferencias", async (PreferenciasRequest r, HttpContext http, HarborDbContext db) =>
        {
            var id = ClienteId(http); if (id is null) return Results.Unauthorized();
            if (r.Generos is null || r.Generos.Count is < 1 or > 3) return Results.ValidationProblem(new Dictionary<string, string[]> { ["generos"] = ["Escolha de 1 a 3 gêneros."] });
            var c = await db.Clientes.FindAsync(id); if (c is null) return Results.NotFound(); c.Generos = JsonSerializer.Serialize(r.Generos.Distinct()); await db.SaveChangesAsync(); return Results.Ok(new { generos = Generos(c.Generos) });
        });
        api.MapGet("/assinatura", async (HttpContext http, HarborDbContext db) =>
        {
            var id = ClienteId(http); if (id is null) return Results.Unauthorized();
            var a = await db.Assinaturas.Include(x => x.Plano).FirstOrDefaultAsync(x => x.ClienteId == id);
            return a is null ? Results.NotFound() : Results.Ok(new { a.Id, a.Status, a.CriadaEm, a.ProximaRenovacao, plano = new { a.Plano!.Id, a.Plano.Nome, a.Plano.Periodicidade, a.Plano.Preco } });
        });
        api.MapPost("/assinatura", async (AssinarRequest r, HttpContext http, HarborDbContext db) =>
        {
            var id = ClienteId(http); if (id is null) return Results.Unauthorized();
            var plano = await db.Planos.FirstOrDefaultAsync(x => x.Id == r.PlanoId && x.Ativo); if (plano is null) return Results.NotFound(new { mensagem = "Plano não encontrado." });
            var existente = await db.Assinaturas.FirstOrDefaultAsync(x => x.ClienteId == id); if (existente is not null) return Results.Conflict(new { mensagem = "A conta já possui assinatura." });
            // Sem integração de pagamento configurada, a assinatura fica pendente (RN-08).
            var a = new Assinatura { ClienteId = id.Value, PlanoId = plano.Id, Status = "Pendente", CriadaEm = DateTime.UtcNow, ProximaRenovacao = DateTime.UtcNow.AddMonths(1) };
            db.Assinaturas.Add(a); await db.SaveChangesAsync(); return Results.Created($"/api/assinatura", new { a.Id, a.Status, plano = plano.Nome, plano.Preco });
        });
        api.MapPost("/assinatura/{id:int}/pagamento", async (int id, PagamentoRequest r, HttpContext http, HarborDbContext db, IHostEnvironment env) =>
        {
            if (!env.IsDevelopment()) return Results.Problem("Confirmação simulada disponível somente no ambiente Development.", statusCode: StatusCodes.Status503ServiceUnavailable);
            var clienteId = ClienteId(http); if (clienteId is null) return Results.Unauthorized();
            var a = await db.Assinaturas.Include(x => x.Plano).FirstOrDefaultAsync(x => x.Id == id && x.ClienteId == clienteId);
            if (a is null) return Results.NotFound();
            if (!r.Aprovado) return Results.BadRequest(new { mensagem = "Pagamento não aprovado; a assinatura permanece pendente." });
            a.Status = "Ativa"; a.ProximaRenovacao = DateTime.UtcNow.AddMonths(PeriodicidadeMeses(a.Plano!.Periodicidade));
            await db.SaveChangesAsync(); return Results.Ok(new { a.Id, a.Status, a.ProximaRenovacao });
        });
        api.MapPut("/assinatura/plano", async (AssinarRequest r, HttpContext http, HarborDbContext db) =>
        {
            var id = ClienteId(http); if (id is null) return Results.Unauthorized();
            var a = await db.Assinaturas.FirstOrDefaultAsync(x => x.ClienteId == id && x.Status == "Ativa");
            var p = await db.Planos.FirstOrDefaultAsync(x => x.Id == r.PlanoId && x.Ativo);
            if (a is null || p is null) return Results.NotFound(new { mensagem = "Assinatura ativa ou plano não encontrado." });
            a.PlanoId = p.Id; await db.SaveChangesAsync(); return Results.Ok(new { plano = p.Nome, p.Preco, vigencia = "O novo valor aplica-se à próxima cobrança." });
        });
        api.MapPost("/assinatura/cancelar", async (HttpContext http, HarborDbContext db) =>
        {
            var id = ClienteId(http); if (id is null) return Results.Unauthorized();
            var a = await db.Assinaturas.FirstOrDefaultAsync(x => x.ClienteId == id && x.Status == "Ativa");
            if (a is null) return Results.NotFound(new { mensagem = "Não há assinatura ativa." });
            a.Status = "Cancelada"; await db.SaveChangesAsync(); return Results.Ok(new { a.Status, condicao = "A assinatura deixa de renovar. O prazo de arrependimento de 7 dias aplica-se conforme o CDC." });
        });
        api.MapGet("/caixas", async (HttpContext http, HarborDbContext db) =>
        {
            var id = ClienteId(http); if (id is null) return Results.Unauthorized();
            var caixas = await db.Caixas.Include(x => x.Livro).Where(x => x.ClienteId == id).OrderByDescending(x => x.CriadaEm)
                .Select(x => new { x.Id, x.Status, x.CodigoRastreio, x.CriadaEm, livro = x.Livro == null ? null : new { x.Livro.Id, x.Livro.Titulo, x.Livro.Autor, x.Livro.Sinopse, x.Livro.Categoria, x.Livro.Estoque } }).ToListAsync();
            return Results.Ok(caixas);
        });
        api.MapPost("/avaliacoes", async (AvaliacaoRequest r, HttpContext http, HarborDbContext db) =>
        {
            var id = ClienteId(http); if (id is null) return Results.Unauthorized();
            var entregue = await db.Caixas.AnyAsync(x => x.ClienteId == id && x.LivroId == r.LivroId && x.Status == "Entregue");
            if (!entregue || r.Nota is < 1 or > 5) return Results.BadRequest(new { mensagem = "Avalie de 1 a 5 somente um livro recebido." });
            var av = new Avaliacao { ClienteId = id.Value, LivroId = r.LivroId, Nota = r.Nota, Comentario = r.Comentario?.Trim() ?? "", CriadaEm = DateTime.UtcNow }; db.Avaliacoes.Add(av); await db.SaveChangesAsync(); return Results.Created($"/api/avaliacoes/{av.Id}", av);
        });
        api.MapGet("/comunidade", async (HarborDbContext db) =>
        {
            var mensagens = await db.Mensagens.AsNoTracking().OrderByDescending(x => x.CriadaEm).Take(100)
                .Select(x => new ComunidadeItem(x.Id, "Mensagem", x.Nome, x.Texto, x.CriadaEm)).ToListAsync();
            var avaliacoesRaw = await (from a in db.Avaliacoes.AsNoTracking()
                                       join c in db.Clientes.AsNoTracking() on a.ClienteId equals c.Id
                                       join l in db.Livros.AsNoTracking() on a.LivroId equals l.Id
                                       orderby a.CriadaEm descending
                                       select new { a.Id, c.Nome, l.Titulo, a.Nota, a.Comentario, a.CriadaEm })
                .Take(100).ToListAsync();
            var avaliacoes = avaliacoesRaw.Select(a => new ComunidadeItem(-a.Id, "Avaliação", a.Nome, $"{a.Titulo} • {a.Nota}/5 — {a.Comentario}", a.CriadaEm));
            return Results.Ok(mensagens.Concat(avaliacoes).OrderBy(x => x.CriadaEm).TakeLast(100));
        });
        api.MapPost("/comunidade", async (MensagemRequest r, HttpContext http, HarborDbContext db) =>
        {
            var id = ClienteId(http); if (id is null) return Results.Unauthorized();
            if (!await db.Assinaturas.AnyAsync(x => x.ClienteId == id && x.Status == "Ativa")) return Results.Forbid();
            if (string.IsNullOrWhiteSpace(r.Texto) || r.Texto.Length > 1000) return Results.BadRequest(new { mensagem = "A mensagem deve ter até 1000 caracteres." });
            var c = await db.Clientes.FindAsync(id); var m = new Mensagem { ClienteId = id.Value, Nome = c!.Nome, Texto = r.Texto.Trim(), CriadaEm = DateTime.UtcNow }; db.Mensagens.Add(m); await db.SaveChangesAsync(); return Results.Created($"/api/comunidade/{m.Id}", m);
        });
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
        operacao.MapPost("/livros", async (LivroRequest r, HarborDbContext db) => { if (r.Estoque < 0 || string.IsNullOrWhiteSpace(r.Titulo)) return Results.BadRequest(); var l = new Livro { Titulo = r.Titulo.Trim(), Autor = r.Autor.Trim(), Sinopse = r.Sinopse.Trim(), Categoria = r.Categoria.Trim(), Estoque = r.Estoque }; db.Livros.Add(l); await db.SaveChangesAsync(); return Results.Created($"/api/livros/{l.Id}", l); });
        operacao.MapPost("/caixas", async (MontarCaixaRequest r, HarborDbContext db) =>
        {
            var sub = await db.Assinaturas.Include(x => x.Cliente).FirstOrDefaultAsync(x => x.ClienteId == r.ClienteId && x.Status == "Ativa");
            if (sub is null) return Results.BadRequest(new { mensagem = "É necessária uma assinatura ativa." });
            Livro? livro;
            if (r.LivroId > 0) livro = await db.Livros.FirstOrDefaultAsync(x => x.Id == r.LivroId && x.Estoque > 0);
            else
            {
                var generos = Generos(sub.Cliente!.Generos);
                livro = await db.Livros.Where(x => x.Estoque > 0 && generos.Contains(x.Categoria)).OrderBy(x => x.Id).FirstOrDefaultAsync();
            }
            if (livro is null) return Results.Conflict(new { mensagem = "Não há obra compatível em estoque; sinalizar reposição." });
            livro.Estoque--;
            var caixa = new Caixa { ClienteId = r.ClienteId, LivroId = livro.Id, Responsavel = r.Responsavel.Trim(), CriadaEm = DateTime.UtcNow };
            db.Caixas.Add(caixa); await db.SaveChangesAsync();
            return Results.Created($"/api/caixas/{caixa.Id}", new { caixa.Id, caixa.Status, livro = livro.Titulo, responsavel = r.Responsavel });
        });
        operacao.MapPut("/caixas/{id:int}/envio", async (int id, EnvioRequest r, HarborDbContext db) => { var c = await db.Caixas.FindAsync(id); if (c is null) return Results.NotFound(); if (string.IsNullOrWhiteSpace(r.CodigoRastreio)) return Results.BadRequest(); c.CodigoRastreio = r.CodigoRastreio.Trim(); c.Status = "Em transporte"; await db.SaveChangesAsync(); return Results.Ok(c); });
        operacao.MapPut("/caixas/{id:int}/status", async (int id, StatusRequest r, HarborDbContext db) => { var c = await db.Caixas.FindAsync(id); if (c is null) return Results.NotFound(); c.Status = r.Status; await db.SaveChangesAsync(); return Results.Ok(c); });
        operacao.MapPost("/curadoria", async (CuradoriaRequest r, HarborDbContext db) =>
        {
            var assinatura = await db.Assinaturas.Include(x => x.Cliente).FirstOrDefaultAsync(x => x.ClienteId == r.ClienteId && x.Status == "Ativa");
            if (assinatura is null) return Results.BadRequest(new { mensagem = "É necessária uma assinatura ativa." });
            var generos = Generos(assinatura.Cliente!.Generos);
            var livro = await db.Livros.Where(x => x.Estoque > 0 && generos.Contains(x.Categoria)).OrderBy(x => x.Id).FirstOrDefaultAsync();
            if (livro is null) return Results.Conflict(new { mensagem = "Não há obra compatível em estoque; reposição necessária." });
            return Results.Ok(new { livro.Id, livro.Titulo, livro.Autor, livro.Sinopse, livro.Categoria });
        });
        operacao.MapPut("/caixas/{id:int}/tratativa", async (int id, TratativaRequest r, HarborDbContext db) =>
        {
            var caixa = await db.Caixas.FindAsync(id); if (caixa is null) return Results.NotFound();
            if (r.Resultado is not ("Reposição" or "Reembolso")) return Results.BadRequest(new { mensagem = "Informe Reposição ou Reembolso." });
            caixa.Status = r.Resultado == "Reposição" ? "Reposição em andamento" : "Reembolsada";
            await db.SaveChangesAsync(); return Results.Ok(new { caixa.Id, caixa.Status, tratativa = r.Detalhes });
        });
    }

    private static async Task<IResult> CriarConta(ContaRequest r, HarborDbContext db)
    {
        if (string.IsNullOrWhiteSpace(r.Nome) || !r.Email.Contains('@') || r.Senha.Length < 8 || string.IsNullOrWhiteSpace(r.Endereco) || !r.Consentimento) return Results.ValidationProblem(new Dictionary<string, string[]> { ["cadastro"] = ["Informe nome, e-mail válido, senha com 8 caracteres, endereço e aceite a política de privacidade."] });
        var email = r.Email.Trim().ToLowerInvariant(); var indice = HarborCrypto.EmailIndex(email); if (await db.Clientes.AnyAsync(x => x.EmailIndice == indice)) return Results.Conflict(new { mensagem = "Este e-mail já está cadastrado." });
        var c = new Cliente { Nome = r.Nome.Trim(), Email = email, EmailIndice = indice, Endereco = r.Endereco.Trim(), SenhaHash = BCrypt.Net.BCrypt.HashPassword(r.Senha), CriadoEm = DateTime.UtcNow };
        db.Clientes.Add(c); await db.SaveChangesAsync(); return Results.Created("/api/perfil", await NovaSessao(c, db));
    }
    private static async Task<IResult> Entrar(LoginRequest r, HarborDbContext db)
    {
        var c = await db.Clientes.FirstOrDefaultAsync(x => x.EmailIndice == HarborCrypto.EmailIndex(r.Email));
        if (c is null || c.SenhaHash.Length == 0 || !BCrypt.Net.BCrypt.Verify(r.Senha, c.SenhaHash)) return Results.Unauthorized();
        return Results.Ok(await NovaSessao(c, db));
    }
    private static async Task<object> NovaSessao(Cliente cliente, HarborDbContext db)
    {
        var refresh = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32)).TrimEnd('=').Replace('+', '-').Replace('/', '_');
        db.RefreshTokens.Add(new RefreshToken { ClienteId = cliente.Id, TokenHash = HashRefresh(refresh), CriadaEm = DateTime.UtcNow, ExpiraEm = DateTime.UtcNow.AddDays(30) });
        await db.SaveChangesAsync();
        return new { token = Token(cliente.Id), refreshToken = refresh, cliente = new { cliente.Id, cliente.Nome, cliente.Email } };
    }
    private static string HashRefresh(string refresh) => Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(refresh)));
    private static string Token(int id) { var exp = DateTimeOffset.UtcNow.AddMinutes(15).ToUnixTimeSeconds(); var payload = $"{id}:{exp}"; var key = Encoding.UTF8.GetBytes(Environment.GetEnvironmentVariable("HARBOR_TOKEN_KEY") ?? "harbor-dev-key-change-before-deploy-32"); var sig = HMACSHA256.HashData(key, Encoding.UTF8.GetBytes(payload)); return Convert.ToBase64String(Encoding.UTF8.GetBytes(payload + ":" + Convert.ToHexString(sig))); }
    private static int? ClienteId(HttpContext http) { var raw = http.Request.Headers.Authorization.ToString(); if (!raw.StartsWith("Bearer ")) return null; try { var decoded = Encoding.UTF8.GetString(Convert.FromBase64String(raw[7..])).Split(':'); var payload = $"{decoded[0]}:{decoded[1]}"; var key = Encoding.UTF8.GetBytes(Environment.GetEnvironmentVariable("HARBOR_TOKEN_KEY") ?? "harbor-dev-key-change-before-deploy-32"); var expected = Convert.ToHexString(HMACSHA256.HashData(key, Encoding.UTF8.GetBytes(payload))); return long.Parse(decoded[1]) > DateTimeOffset.UtcNow.ToUnixTimeSeconds() && CryptographicOperations.FixedTimeEquals(Encoding.UTF8.GetBytes(expected), Encoding.UTF8.GetBytes(decoded[2])) ? int.Parse(decoded[0]) : null; } catch { return null; } }
    private static string[] Generos(string json) => JsonSerializer.Deserialize<string[]>(json) ?? [];
    private static int PeriodicidadeMeses(string periodicidade) => periodicidade.Equals("Anual", StringComparison.OrdinalIgnoreCase) ? 12 : periodicidade.Equals("Trimestral", StringComparison.OrdinalIgnoreCase) ? 3 : 1;
}

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
public sealed record PagamentoRequest(bool Aprovado);
public sealed record CuradoriaRequest(int ClienteId);
public sealed record TratativaRequest(string Resultado, string Detalhes);
public sealed record ComunidadeItem(int Id, string Tipo, string Nome, string Texto, DateTime CriadaEm);
