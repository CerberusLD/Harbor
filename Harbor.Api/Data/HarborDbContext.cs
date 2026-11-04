using Harbor.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace Harbor.Api.Data;

public sealed class HarborDbContext(DbContextOptions<HarborDbContext> options) : DbContext(options)
{
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
        model.Entity<Cliente>().HasIndex(x => x.EmailIndice).IsUnique();
        model.Entity<Cliente>().Property(x => x.Nome).HasConversion(HarborCrypto.Converter);
        model.Entity<Cliente>().Property(x => x.Email).HasConversion(HarborCrypto.Converter);
        model.Entity<Cliente>().Property(x => x.Endereco).HasConversion(HarborCrypto.Converter);
        model.Entity<Cliente>().Property(x => x.Generos).HasConversion(HarborCrypto.Converter);
        model.Entity<Plano>().Property(x => x.Preco).HasColumnType("REAL");
        model.Entity<Assinatura>().HasOne(x => x.Cliente).WithOne(x => x.Assinatura).HasForeignKey<Assinatura>(x => x.ClienteId);
        model.Entity<Caixa>().HasOne(x => x.Livro).WithMany().HasForeignKey(x => x.LivroId);
        model.Entity<Mensagem>().Property(x => x.Nome).HasConversion(HarborCrypto.Converter);
        model.Entity<Mensagem>().Property(x => x.Texto).HasConversion(HarborCrypto.Converter);
        model.Entity<Avaliacao>().Property(x => x.Comentario).HasConversion(HarborCrypto.Converter);
        model.Entity<Caixa>().Property(x => x.Responsavel).HasConversion(HarborCrypto.Converter);
        model.Entity<RefreshToken>().HasIndex(x => x.TokenHash).IsUnique();
        model.Entity<RefreshToken>().HasOne(x => x.Cliente).WithMany().HasForeignKey(x => x.ClienteId);
    }
}
