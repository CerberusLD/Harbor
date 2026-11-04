namespace Harbor.Api.Models;

// Modelos do MVP. Cada entidade fica simples e espelha os conceitos do produto.
public sealed class Cliente
{
    public int Id { get; set; }
    public string Nome { get; set; } = "";
    public string Email { get; set; } = "";
    public string EmailIndice { get; set; } = "";
    public string SenhaHash { get; set; } = "";
    public string Endereco { get; set; } = "";
    public string Generos { get; set; } = "[]";
    public DateTime CriadoEm { get; set; }
    public Assinatura? Assinatura { get; set; }
}

public sealed class Plano
{
    public int Id { get; set; }
    public string Nome { get; set; } = "";
    public string Periodicidade { get; set; } = "Mensal";
    public decimal Preco { get; set; }
    public string Descricao { get; set; } = "";
    public bool Ativo { get; set; } = true;
}

public sealed class Assinatura
{
    public int Id { get; set; }
    public int ClienteId { get; set; }
    public Cliente? Cliente { get; set; }
    public int PlanoId { get; set; }
    public Plano? Plano { get; set; }
    public string Status { get; set; } = "Pendente";
    public DateTime CriadaEm { get; set; }
    public DateTime ProximaRenovacao { get; set; }
}

public sealed class Livro
{
    public int Id { get; set; }
    public string Titulo { get; set; } = "";
    public string Autor { get; set; } = "";
    public string Sinopse { get; set; } = "";
    public string Categoria { get; set; } = "";
    public int Estoque { get; set; }
}

public sealed class Caixa
{
    public int Id { get; set; }
    public int ClienteId { get; set; }
    public int LivroId { get; set; }
    public string Status { get; set; } = "Em preparação";
    public string CodigoRastreio { get; set; } = "";
    public string Responsavel { get; set; } = "";
    public DateTime CriadaEm { get; set; }
    public Livro? Livro { get; set; }
}

public sealed class Avaliacao
{
    public int Id { get; set; }
    public int ClienteId { get; set; }
    public int LivroId { get; set; }
    public int Nota { get; set; }
    public string Comentario { get; set; } = "";
    public DateTime CriadaEm { get; set; }
}

public sealed class Mensagem
{
    public int Id { get; set; }
    public int ClienteId { get; set; }
    public string Nome { get; set; } = "";
    public string Texto { get; set; } = "";
    public DateTime CriadaEm { get; set; }
}

public sealed class RefreshToken
{
    public int Id { get; set; }
    public int ClienteId { get; set; }
    public Cliente? Cliente { get; set; }
    public string TokenHash { get; set; } = "";
    public DateTime CriadaEm { get; set; }
    public DateTime ExpiraEm { get; set; }
    public bool Revogada { get; set; }
}
