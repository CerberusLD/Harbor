package br.com.harbor

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

data class LoginBody(val email:String,val senha:String)
data class ContaBody(val nome:String,val email:String,val senha:String,val endereco:String,val consentimento:Boolean)
data class TokenReply(val token:String,val refreshToken:String,val cliente:ClienteReply)
data class ClienteReply(val id:Int,val nome:String,val email:String)
data class PlanoReply(val id:Int,val nome:String,val periodicidade:String,val preco:Double,val descricao:String)
data class PreferenciasBody(val generos:List<String>)
data class AssinarBody(val planoId:Int)
data class AssinaturaReply(val id:Int,val status:String,val plano:String,val preco:Double)
data class PagamentoBody(val aprovado:Boolean)
data class CaixaReply(val id:Int,val status:String,val codigoRastreio:String,val livro:LivroReply?,val criadaEm:String)
data class LivroReply(val id:Int,val titulo:String,val autor:String,val sinopse:String,val categoria:String,val estoque:Int)
data class MensagemBody(val texto:String)
data class MensagemReply(val id:Int,val tipo:String,val nome:String,val texto:String,val criadaEm:String)
data class AvaliacaoBody(val livroId:Int,val nota:Int,val comentario:String)
data class PerfilReply(val id:Int,val nome:String,val email:String,val endereco:String,val generos:List<String>)
data class PerfilBody(val nome:String,val endereco:String)
data class RefreshBody(val refreshToken:String)
data class PedidoItemBody(val livroId:Int,val quantidade:Int)
data class PedidoBody(val cep:String,val cupom:String,val formaPagamento:String,val itens:List<PedidoItemBody>)
data class PedidoReply(val id:Int,val status:String,val total:Double)

interface HarborApi {
    @POST("api/contas") suspend fun cadastrar(@Body conta:ContaBody):TokenReply
    @POST("api/sessao") suspend fun entrar(@Body login:LoginBody):TokenReply
    @POST("api/sessao/renovar") suspend fun renovar(@Body body:RefreshBody):TokenReply
    @POST("api/sessao/sair") suspend fun sair(@Body body:RefreshBody)
    @GET("api/planos") suspend fun planos():List<PlanoReply>
    @GET("api/perfil") suspend fun perfil(@Header("Authorization") auth:String):PerfilReply
    @PUT("api/perfil") suspend fun atualizarPerfil(@Header("Authorization") auth:String,@Body body:PerfilBody):Map<String,String>
    @GET("api/livros") suspend fun livros():List<LivroReply>
    @GET("api/caixas") suspend fun caixas(@Header("Authorization") auth:String):List<CaixaReply>
    @POST("api/preferencias") suspend fun salvarPreferencias(@Header("Authorization") auth:String,@Body body:PreferenciasBody):Map<String,List<String>>
    @POST("api/assinatura") suspend fun assinar(@Header("Authorization") auth:String,@Body body:AssinarBody):AssinaturaReply
    @POST("api/assinatura/{id}/pagamento") suspend fun confirmarPagamento(@Header("Authorization") auth:String,@Path("id") id:Int,@Body body:PagamentoBody)
    @PUT("api/assinatura/plano") suspend fun alterarPlano(@Header("Authorization") auth:String,@Body body:AssinarBody):Map<String,Any>
    @POST("api/assinatura/cancelar") suspend fun cancelarAssinatura(@Header("Authorization") auth:String):Map<String,String>
    @GET("api/assinatura") suspend fun assinatura(@Header("Authorization") auth:String):Map<String,Any>
    @GET("api/comunidade") suspend fun comunidade(@Header("Authorization") auth:String):List<MensagemReply>
    @POST("api/comunidade") suspend fun publicar(@Header("Authorization") auth:String,@Body body:MensagemBody)
    @POST("api/avaliacoes") suspend fun avaliar(@Header("Authorization") auth:String,@Body body:AvaliacaoBody)
    @DELETE("api/perfil") suspend fun excluirPerfil(@Header("Authorization") auth:String)
    @POST("api/pedidos") suspend fun criarPedido(@Header("Authorization") auth:String,@Body body:PedidoBody):PedidoReply
    @POST("api/pedidos/{id}/pagamento") suspend fun pagarPedido(@Header("Authorization") auth:String,@Path("id") id:Int,@Body body:PagamentoBody)
}

/** O padrão 10.0.2.2 aponta para o host do desenvolvimento a partir do emulador Android. */
object HarborApiClient {
    private val http= OkHttpClient.Builder().build()
    val api:HarborApi=Retrofit.Builder().baseUrl(BuildConfig.HARBOR_API_BASE_URL).client(http).addConverterFactory(GsonConverterFactory.create()).build().create(HarborApi::class.java)
}
