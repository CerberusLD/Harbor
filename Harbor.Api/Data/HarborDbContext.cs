using Harbor.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace Harbor.Api.Data;

/// <summary>
/// Contexto do EF Core: mapeia as entidades para as tabelas do SQLite.
/// Cada propriedade DbSet corresponde a uma tabela; as regras de relacionamento
/// ficam em <see cref="OnModelCreating"/>.
/// </summary>
public sealed class HarborDbContext(DbContextOptions<HarborDbContext> options) : DbContext(options)
{
    // Tabelas (uma por entidade do domínio)
    public DbSet<Cliente> Clientes => Set<Cliente>();
    public DbSet<Plano> Planos => Set<Plano>();
    public DbSet<Assinatura> Assinaturas => Set<Assinatura>();
    public DbSet<Livro> Livros => Set<Livro>();
    public DbSet<Caixa> Caixas => Set<Caixa>();
    public DbSet<Avaliacao> Avaliacoes => Set<Avaliacao>();
    public DbSet<Mensagem> Mensagens => Set<Mensagem>();
    public DbSet<RefreshToken> RefreshTokens => Set<RefreshToken>();

    protected override void OnModelCreating(ModelBuilder model)
    {
        // Índice único para login por e-mail (guardado como HMAC em EmailIndice)
        model.Entity<Cliente>().HasIndex(x => x.EmailIndice).IsUnique();

        // Colunas de dados pessoais criptografadas (LGPD): valor de texto <-> bytes cifrados
        model.Entity<Cliente>().Property(x => x.Nome).HasConversion(HarborCrypto.Converter);
        model.Entity<Cliente>().Property(x => x.Email).HasConversion(HarborCrypto.Converter);
        model.Entity<Cliente>().Property(x => x.Endereco).HasConversion(HarborCrypto.Converter);
        model.Entity<Cliente>().Property(x => x.Generos).HasConversion(HarborCrypto.Converter);
        model.Entity<Mensagem>().Property(x => x.Nome).HasConversion(HarborCrypto.Converter);
        model.Entity<Mensagem>().Property(x => x.Texto).HasConversion(HarborCrypto.Converter);
        model.Entity<Avaliacao>().Property(x => x.Comentario).HasConversion(HarborCrypto.Converter);
        model.Entity<Caixa>().Property(x => x.Responsavel).HasConversion(HarborCrypto.Converter);

        // Tipo numérico do preço (SQLite não tem DECIMAL nativo)
        model.Entity<Plano>().Property(x => x.Preco).HasColumnType("REAL");

        // RELACIONAMENTOS
        // 1:1 — Cliente <-> Assinatura (uma conta, uma assinatura)
        model.Entity<Assinatura>().HasOne(x => x.Cliente).WithOne(x => x.Assinatura).HasForeignKey<Assinatura>(x => x.ClienteId);

        // 1:N — Livro -> Caixa (um livro aparece em várias caixas)
        model.Entity<Caixa>().HasOne(x => x.Livro).WithMany().HasForeignKey(x => x.LivroId);

        // 1:N — Cliente -> RefreshToken (várias sessões por cliente)
        model.Entity<RefreshToken>().HasIndex(x => x.TokenHash).IsUnique();
        model.Entity<RefreshToken>().HasOne(x => x.Cliente).WithMany().HasForeignKey(x => x.ClienteId);

        // N:N — Cliente <-> Livro é representado por duas 1:N (Caixa e Avaliacao),
        // cada uma com payload próprio (status/rastreamento e nota/comentário).
        // Os demais relacionamentos (Plano -> Assinatura, Cliente -> Caixa,
        // Cliente -> Avaliacao, Cliente -> Mensagem) são resolvidos por convenção
        // do EF Core a partir das chaves estrangeiras nas entidades.
    }
}
