using System.Security.Cryptography;
using System.Text;
using Microsoft.EntityFrameworkCore.Storage.ValueConversion;

namespace Harbor.Api.Data;

/// <summary>Criptografa campos pessoais com AES-256-GCM. A chave vem do ambiente.</summary>
public static class HarborCrypto
{
    private const string Prefix = "enc:v1:";
    private static byte[]? _key;
    private static byte[]? _indexKey;
    public static readonly ValueConverter<string, string> Converter = new(
        value => Encrypt(value), value => Decrypt(value));

    public static void Configure(bool development, string? secret)
    {
        if (string.IsNullOrWhiteSpace(secret))
        {
            if (!development) throw new InvalidOperationException("HARBOR_DATA_KEY deve ser definido fora do ambiente Development.");
            secret = "harbor-local-development-data-key-do-not-deploy";
        }
        _key = SHA256.HashData(Encoding.UTF8.GetBytes(secret + ":harbor-data"));
        _indexKey = SHA256.HashData(Encoding.UTF8.GetBytes(secret + ":harbor-email-index"));
    }

    public static string Encrypt(string value)
    {
        if (value.StartsWith(Prefix, StringComparison.Ordinal)) return value;
        var nonce = RandomNumberGenerator.GetBytes(12);
        var plain = Encoding.UTF8.GetBytes(value);
        var cipher = new byte[plain.Length];
        var tag = new byte[16];
        using var aes = new AesGcm(Key(), tag.Length);
        aes.Encrypt(nonce, plain, cipher, tag);
        return Prefix + Convert.ToBase64String([.. nonce, .. tag, .. cipher]);
    }

    public static string Decrypt(string value)
    {
        if (!value.StartsWith(Prefix, StringComparison.Ordinal)) return value; // migração dos registros anteriores
        var bytes = Convert.FromBase64String(value[Prefix.Length..]);
        var plain = new byte[bytes.Length - 28];
        using var aes = new AesGcm(Key(), 16);
        aes.Decrypt(bytes.AsSpan(0, 12), bytes.AsSpan(28), bytes.AsSpan(12, 16), plain);
        return Encoding.UTF8.GetString(plain);
    }

    public static string EmailIndex(string email)
    {
        var normalized = Encoding.UTF8.GetBytes(email.Trim().ToLowerInvariant());
        return Convert.ToHexString(HMACSHA256.HashData(_indexKey ?? throw new InvalidOperationException("HarborCrypto ainda não foi configurado."), normalized));
    }

    private static byte[] Key() => _key ?? throw new InvalidOperationException("HarborCrypto ainda não foi configurado.");
}
