namespace Harbor.Api.Models;

// ===========================================================================
// Entidades do domínio Harbor.
// Cada classe espelha um conceito do produto e vira uma tabela no SQLite.
// Convenções do EF Core: toda propriedade "Id" é a chave primária; propriedades
// de navegação (ex.: Cliente, Livro) definem os relacionamentos.
// ===========================================================================

/// <summary>Leitor da plataforma: conta de acesso e preferências literárias.</summary>
public sealed class Cliente
{
    public int Id { get; set; }                          // chave primária
    public string Nome { get; set; } = "";               // criptografado (LGPD)
    public string Email { get; set; } = "";              // criptografado (LGPD)
    public string EmailIndice { get; set; } = "";         // HMAC do e-mail, usado no login
    public string SenhaHash { get; set; } = "";           // bcrypt, nunca a senha em texto
    public string Endereco { get; set; } = "";           // criptografado (LGPD)
    public string Generos { get; set; } = "[]";           // JSON com 1 a 3 gêneros
    public DateTime CriadoEm { get; set; }
    public Assinatura? Assinatura { get; set; }           // 1:1 com Assinatura
}

/// <summary>Plano de assinatura (Mensal, Trimestral ou Anual).</summary>
public sealed class Plano
{
    public int Id { get; set; }
    public string Nome { get; set; } = "";
    public string Periodicidade { get; set; } = "Mensal";
    public decimal Preco { get; set; }
    public string Descricao { get; set; } = "";
    public bool Ativo { get; set; } = true;
}

/// <summary>Assinatura do leitor: 1:1 com Cliente e N:1 com Plano.</summary>
public sealed class Assinatura
{
    public int Id { get; set; }
    public int ClienteId { get; set; }                    // chave estrangeira (1:1)
    public Cliente? Cliente { get; set; }
    public int PlanoId { get; set; }                      // chave estrangeira (N:1)
    public Plano? Plano { get; set; }
    public string Status { get; set; } = "Pendente";      // Pendente, Ativa ou Cancelada
    public DateTime CriadaEm { get; set; }
    public DateTime ProximaRenovacao { get; set; }
}

/// <summary>Obra do catálogo, com estoque controlado.</summary>
public sealed class Livro
{
    public int Id { get; set; }
    public string Titulo { get; set; } = "";
    public string Autor { get; set; } = "";
    public string Sinopse { get; set; } = "";
    public string Categoria { get; set; } = "";
    public int Estoque { get; set; }
}

/// <summary>
/// Caixa literária entregue ao leitor. É a entidade de junção do N:N
/// entre Cliente e Livro, com payload próprio (status e rastreamento).
/// </summary>
public sealed class Caixa
{
    public int Id { get; set; }
    public int ClienteId { get; set; }                    // N:1 com Cliente
    public int LivroId { get; set; }                      // N:1 com Livro
    public int PedidoId { get; set; }                     // agrupa caixas de um mesmo pedido
    public string Status { get; set; } = "Em preparação";
    public string CodigoRastreio { get; set; } = "";
    public string Responsavel { get; set; } = "";
    public DateTime CriadaEm { get; set; }
    public Livro? Livro { get; set; }
}

/// <summary>
/// Avaliação do leitor sobre um livro. Segunda entidade de junção do N:N
/// entre Cliente e Livro, com payload (nota e comentário).
/// </summary>
public sealed class Avaliacao
{
    public int Id { get; set; }
    public int ClienteId { get; set; }                    // N:1 com Cliente
    public int LivroId { get; set; }                      // N:1 com Livro
    public int Nota { get; set; }                         // de 1 a 5
    public string Comentario { get; set; } = "";          // criptografado (LGPD)
    public DateTime CriadaEm { get; set; }
}

/// <summary>Mensagem do leitor na comunidade Harbor.</summary>
public sealed class Mensagem
{
    public int Id { get; set; }
    public int ClienteId { get; set; }                    // N:1 com Cliente
    public string Nome { get; set; } = "";
    public string Texto { get; set; } = "";
    public DateTime CriadaEm { get; set; }
}

/// <summary>Token de renovação de sessão (rotação a cada uso, válido por 30 dias).</summary>
public sealed class RefreshToken
{
    public int Id { get; set; }
    public int ClienteId { get; set; }                    // N:1 com Cliente
    public Cliente? Cliente { get; set; }
    public string TokenHash { get; set; } = "";           // SHA-256 do token
    public DateTime CriadaEm { get; set; }
    public DateTime ExpiraEm { get; set; }
    public bool Revogada { get; set; }
}
