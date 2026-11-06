using Harbor.Api.Data;
using Harbor.Api.Endpoints;
using Harbor.Api.Models;
using Microsoft.EntityFrameworkCore;

// ===========================================================================
// Ponto de entrada da API.
// Configura o container de serviços, prepara o banco, aplica as migrações
// manuais e sobe o servidor com as rotas de HarborEndpoints.
// ===========================================================================
var builder = WebApplication.CreateBuilder(args);

// Chaves de criptografia/token: em desenvolvimento usam-se valores fixos;
// fora dele, HARBOR_TOKEN_KEY é obrigatória (fail fast em produção).
HarborCrypto.Configure(builder.Environment.IsDevelopment(), Environment.GetEnvironmentVariable("HARBOR_DATA_KEY"));
if (!builder.Environment.IsDevelopment() && string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("HARBOR_TOKEN_KEY")))
    throw new InvalidOperationException("HARBOR_TOKEN_KEY deve ser definido fora do ambiente Development.");

// Conexão SQLite: vem do appsettings ou cai no arquivo local harbor.db
var conexao = builder.Configuration.GetConnectionString("Harbor") ?? "Data Source=harbor.db";

// Serviços: DbContext (EF Core), CORS, Swagger e ProblemDetails
builder.Services.AddDbContext<HarborDbContext>(o => o.UseSqlite(conexao));
builder.Services.AddCors(o => o.AddPolicy("Cliente", p => p.AllowAnyOrigin().AllowAnyHeader().AllowAnyMethod()));
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();
builder.Services.AddProblemDetails();

var app = builder.Build();

// ---------------------------------------------------------------------------
// Inicialização do banco: cria tabelas, aplica migrações manuais e popula o
// catálogo na primeira execução. Em produção, preferir Migrations.
// ---------------------------------------------------------------------------
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<HarborDbContext>();
    db.Database.EnsureCreated();

    // Tabela auxiliar de sessão (não mapeada como DbSet)
    db.Database.ExecuteSqlRaw("""
        CREATE TABLE IF NOT EXISTS RefreshTokens (
            Id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            ClienteId INTEGER NOT NULL,
            TokenHash TEXT NOT NULL,
            CriadaEm TEXT NOT NULL,
            ExpiraEm TEXT NOT NULL,
            Revogada INTEGER NOT NULL,
            FOREIGN KEY (ClienteId) REFERENCES Clientes (Id) ON DELETE CASCADE
        )
        """);
    db.Database.ExecuteSqlRaw("CREATE UNIQUE INDEX IF NOT EXISTS IX_RefreshTokens_TokenHash ON RefreshTokens (TokenHash)");

    // Migrações manuais (banco já em uso)
    MigrarDadosPessoais(db);
    GarantirColunaPedido(db);

    // Seed inicial: planos e livros do catálogo
    if (!db.Planos.Any())
    {
        db.Planos.AddRange(
            new Plano { Nome = "Plano Mensal", Periodicidade = "Mensal", Preco = 99.90m, Descricao = "Uma caixa literária por mês." },
            new Plano { Nome = "Plano Trimestral", Periodicidade = "Trimestral", Preco = 94.90m, Descricao = "Uma caixa por mês, cobrança trimestral." },
            new Plano { Nome = "Plano Anual", Periodicidade = "Anual", Preco = 89.90m, Descricao = "Uma caixa por mês, cobrança anual e acesso VIP." });
        db.Livros.AddRange(
            new Livro { Titulo = "A Biblioteca da Meia-Noite", Autor = "Matt Haig", Categoria = "Fantasia", Sinopse = "Entre a vida e a morte existe uma biblioteca.", Estoque = 20 },
            new Livro { Titulo = "Pequenos Incêndios por Toda Parte", Autor = "Celeste Ng", Categoria = "Ficção", Sinopse = "Duas famílias se aproximam em uma cidade planejada.", Estoque = 14 },
            new Livro { Titulo = "O Jardim Secreto", Autor = "Frances Hodgson Burnett", Categoria = "Clássicos", Sinopse = "Uma descoberta transforma uma casa isolada.", Estoque = 12 });
        db.SaveChanges();
    }

    var catalogoInicial = new[]
    {
        new Livro { Titulo = "Amor em Cartas", Autor = "Helena Monteiro", Categoria = "Romance", Sinopse = "Duas vidas se encontram por meio de cartas esquecidas.", Estoque = 10 },
        new Livro { Titulo = "Sombras no Farol", Autor = "Rafael Nogueira", Categoria = "Suspense", Sinopse = "Uma vila costeira guarda um segredo que retorna a cada maré.", Estoque = 10 },
        new Livro { Titulo = "A Casa Sem Janelas", Autor = "Lia Campos", Categoria = "Terror", Sinopse = "Uma mudança para o campo transforma a rotina em pesadelo.", Estoque = 8 },
        new Livro { Titulo = "Órbita de Papel", Autor = "Caio Ventura", Categoria = "Ficção Científica", Sinopse = "Uma arquivista recebe mensagens enviadas de uma estação distante.", Estoque = 9 },
        new Livro { Titulo = "O Mapa das Marés", Autor = "Marina Torres", Categoria = "Aventura", Sinopse = "Uma expedição segue pistas por ilhas que desaparecem na neblina.", Estoque = 11 }
    };
    foreach (var livro in catalogoInicial)
        if (!db.Livros.Any(x => x.Categoria == livro.Categoria)) db.Livros.Add(livro);
    db.SaveChanges();
}

// ---------------------------------------------------------------------------
// Pipeline HTTP
// ---------------------------------------------------------------------------
app.UseExceptionHandler();
app.UseCors("Cliente");
app.UseSwagger();
app.UseSwaggerUI();
app.MapGet("/", () => Results.Redirect("/swagger")).ExcludeFromDescription();
app.MapGet("/health", () => Results.Ok(new { status = "ok", servico = "Harbor API", horarioUtc = DateTime.UtcNow }));
app.MapHarbor();
app.Run();

// ===========================================================================
// Migrações manuais
// ===========================================================================

/// <summary>
/// Migração de uma base antiga: adiciona EmailIndice, recifra os dados pessoais
/// (que antes eram texto puro) e recria o índice único de e-mail.
/// </summary>
static void MigrarDadosPessoais(HarborDbContext db)
{
    var connection = db.Database.GetDbConnection();
    connection.Open();

    // 1) Garante a coluna EmailIndice
    using (var columns = connection.CreateCommand())
    {
        columns.CommandText = "PRAGMA table_info('Clientes')";
        using var reader = columns.ExecuteReader();
        var temColuna = false;
        while (reader.Read()) if (reader.GetString(1) == "EmailIndice") temColuna = true;
        reader.Close();
        if (!temColuna)
        {
            using var alter = connection.CreateCommand();
            alter.CommandText = "ALTER TABLE Clientes ADD COLUMN EmailIndice TEXT NOT NULL DEFAULT ''";
            alter.ExecuteNonQuery();
        }
    }

    // 2) Recifra os dados pessoais de Clientes
    using (var select = connection.CreateCommand())
    {
        select.CommandText = "SELECT Id, Nome, Email, Endereco, Generos, EmailIndice FROM Clientes";
        using var reader = select.ExecuteReader();
        var rows = new List<(int Id, string Nome, string Email, string Endereco, string Generos, string EmailIndice)>();
        while (reader.Read())
            rows.Add((reader.GetInt32(0), reader.GetString(1), reader.GetString(2), reader.GetString(3), reader.GetString(4), reader.GetString(5)));
        reader.Close();

        foreach (var row in rows)
        {
            var emailPlano = HarborCrypto.Decrypt(row.Email);
            using var update = connection.CreateCommand();
            update.CommandText = "UPDATE Clientes SET Nome=$nome, Email=$email, Endereco=$endereco, Generos=$generos, EmailIndice=$indice WHERE Id=$id";
            foreach (var (nome, valor) in new[]
            {
                ("$nome", HarborCrypto.Encrypt(row.Nome)),
                ("$email", HarborCrypto.Encrypt(emailPlano)),
                ("$endereco", HarborCrypto.Encrypt(row.Endereco)),
                ("$generos", HarborCrypto.Encrypt(HarborCrypto.Decrypt(row.Generos))),
                ("$indice", HarborCrypto.EmailIndex(emailPlano)),
                ("$id", row.Id.ToString())
            })
            {
                var parameter = update.CreateParameter();
                parameter.ParameterName = nome;
                parameter.Value = valor;
                update.Parameters.Add(parameter);
            }
            update.ExecuteNonQuery();
        }
    }

    // 3) Recifra as demais colunas com dados pessoais
    CriptografarColuna(connection, "Mensagens", "Nome");
    CriptografarColuna(connection, "Mensagens", "Texto");
    CriptografarColuna(connection, "Avaliacoes", "Comentario");
    CriptografarColuna(connection, "Caixas", "Responsavel");

    // 4) Recria o índice único de e-mail
    using var indexes = connection.CreateCommand();
    indexes.CommandText = "DROP INDEX IF EXISTS IX_Clientes_Email; CREATE UNIQUE INDEX IF NOT EXISTS IX_Clientes_EmailIndice ON Clientes (EmailIndice)";
    indexes.ExecuteNonQuery();
    connection.Close();
}

/// <summary>Adiciona a coluna PedidoId em Caixas (bases criadas antes do recurso de pedidos).</summary>
static void GarantirColunaPedido(HarborDbContext db)
{
    var connection = db.Database.GetDbConnection();
    connection.Open();
    using (var columns = connection.CreateCommand())
    {
        columns.CommandText = "PRAGMA table_info('Caixas')";
        using var reader = columns.ExecuteReader();
        var existe = false;
        while (reader.Read()) if (reader.GetString(1) == "PedidoId") existe = true;
        reader.Close();
        if (!existe)
        {
            using var alter = connection.CreateCommand();
            alter.CommandText = "ALTER TABLE Caixas ADD COLUMN PedidoId INTEGER NOT NULL DEFAULT 0";
            alter.ExecuteNonQuery();
        }
    }
    connection.Close();
}

/// <summary>Reescreve uma coluna aplicando a cifra (idempotente: descriptografa e recifra).</summary>
static void CriptografarColuna(System.Data.Common.DbConnection connection, string tabela, string coluna)
{
    using var select = connection.CreateCommand();
    select.CommandText = $"SELECT Id, {coluna} FROM {tabela}";
    using var reader = select.ExecuteReader();
    var rows = new List<(int Id, string Value)>();
    while (reader.Read()) rows.Add((reader.GetInt32(0), reader.GetString(1)));
    reader.Close();
    foreach (var row in rows)
    {
        using var update = connection.CreateCommand();
        update.CommandText = $"UPDATE {tabela} SET {coluna}=$value WHERE Id=$id";
        var value = update.CreateParameter();
        value.ParameterName = "$value";
        value.Value = HarborCrypto.Encrypt(HarborCrypto.Decrypt(row.Value));
        update.Parameters.Add(value);
        var id = update.CreateParameter();
        id.ParameterName = "$id";
        id.Value = row.Id;
        update.Parameters.Add(id);
        update.ExecuteNonQuery();
    }
}
